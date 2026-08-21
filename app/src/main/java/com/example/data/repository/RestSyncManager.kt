package com.example.data.repository

import com.example.data.local.WeHabitaDatabase
import com.example.data.local.entities.*
import com.example.data.remote.api.ApiClient
import com.example.data.remote.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RestSyncManager(private val db: WeHabitaDatabase) {
    private val TAG = "WeHabita:RestSync"

    private val userDao = db.userDao()
    private val homeDao = db.homeDao()
    private val homeMemberDao = db.homeMemberDao()
    private val privilegeDao = db.privilegeDao()
    private val taskDao = db.taskDao()
    private val occurrenceDao = db.taskOccurrenceDao()
    private val penaltyDao = db.penaltyDao()
    private val problemDao = db.problemDao()
    private val needListDao = db.needListDao()
    private val goalDao = db.goalDao()
    private val syncQueueDao = db.syncQueueDao()

    /**
     * Diagnostic test for the REST API server connection.
     * Validates DNS, TCP socket connectivity to the host:port, and HTTP GET /api/health endpoint.
     */
    suspend fun testApiConnection(customUrl: String? = null): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val targetUrl = customUrl ?: ApiClient.getBaseUrl()
        SyncLogCollector.i(TAG, "=== Iniciando prueba de diagnóstico REST API ===")
        SyncLogCollector.i(TAG, "Base URL configurada: $targetUrl")

        try {
            val url = URL(if (targetUrl.endsWith("/")) targetUrl else "$targetUrl/")
            val host = url.host
            val port = if (url.port != -1) url.port else (if (url.protocol == "https") 443 else 80)

            // Step 1: DNS Resolution
            SyncLogCollector.d(TAG, "Resolviendo host '$host'...")
            val inetAddress = InetAddress.getByName(host)
            SyncLogCollector.d(TAG, "Host '$host' resuelto a IP: ${inetAddress.hostAddress}")

            // Step 2: TCP Socket Ping
            SyncLogCollector.d(TAG, "Probando socket TCP hacia $host:$port (Timeout 3500ms)...")
            val startMs = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(inetAddress, port), 3500)
            }
            val elapsed = System.currentTimeMillis() - startMs
            SyncLogCollector.s(TAG, "Socket TCP conectado exitosamente con $host:$port en ${elapsed}ms.")

            // Step 3: API Health HTTP Request
            SyncLogCollector.d(TAG, "Enviando GET ${url}health...")
            ApiClient.setBaseUrl(targetUrl)
            val response = ApiClient.service.checkHealth()

            if (response.isSuccessful) {
                val body = response.body()
                val serverStatus = body?.data?.status ?: "OK"
                val serviceName = body?.data?.service ?: "WeHabita API"
                SyncLogCollector.s(TAG, "Servidor REST respondió HTTP ${response.code()}: $serviceName (Estado: $serverStatus)")
                Pair(true, "Conexión exitosa con el servidor REST ($serviceName - Estado: $serverStatus)")
            } else {
                val err = response.errorBody()?.string() ?: "Código ${response.code()}"
                SyncLogCollector.w(TAG, "Servidor respondió con error HTTP ${response.code()}: $err")
                Pair(false, "El servidor respondió con HTTP ${response.code()}: $err")
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Fallo al conectar con el servidor REST: ${e.message}", e)
            Pair(false, "Error de conexión: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Enqueues an offline action into SQLite so it can be replayed when connection is restored.
     */
    suspend fun enqueueOfflineAction(
        endpoint: String,
        httpMethod: String,
        payloadJson: String?,
        accion: String,
        descripcion: String
    ) = withContext(Dispatchers.IO) {
        try {
            val entity = SyncQueueEntity(
                endpoint = endpoint,
                http_method = httpMethod,
                payload_json = payloadJson,
                accion = accion,
                descripcion = descripcion,
                fecha_creacion = System.currentTimeMillis()
            )
            syncQueueDao.enqueue(entity)
            SyncLogCollector.i(TAG, "[Offline Queue] Acción encolada para sincronización: $descripcion ($accion)")
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error al encolar acción offline: ${e.message}", e)
        }
    }

    /**
     * Processes all pending mutations in the offline queue and sends them to the REST API.
     */
    suspend fun processPendingQueue(): Int = withContext(Dispatchers.IO) {
        val pending = syncQueueDao.getAllPendingList()
        if (pending.isEmpty()) {
            return@withContext 0
        }

        SyncLogCollector.i(TAG, "Procesando ${pending.size} operaciones pendientes en cola offline...")
        var successful = 0
        val client = ApiClient.getOkHttpClient()
        val baseUrl = ApiClient.getBaseUrl()

        for (item in pending) {
            try {
                val fullUrl = if (item.endpoint.startsWith("http")) {
                    item.endpoint
                } else {
                    val cleanEndpoint = item.endpoint.removePrefix("/")
                    baseUrl + cleanEndpoint
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = item.payload_json?.toRequestBody(mediaType)

                val requestBuilder = Request.Builder()
                    .url(fullUrl)
                    .header("Accept", "application/json")

                when (item.http_method.uppercase()) {
                    "POST" -> requestBuilder.post(body ?: "".toRequestBody(mediaType))
                    "PUT" -> requestBuilder.put(body ?: "".toRequestBody(mediaType))
                    "PATCH" -> requestBuilder.patch(body ?: "".toRequestBody(mediaType))
                    "DELETE" -> requestBuilder.delete(body)
                    else -> requestBuilder.get()
                }

                val response = client.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    syncQueueDao.deleteById(item.id)
                    successful++
                    SyncLogCollector.s(TAG, "Sincronizado: ${item.descripcion}")
                } else {
                    val errBody = response.body?.string() ?: ""
                    SyncLogCollector.w(TAG, "Error ${response.code} sincronizando ${item.descripcion}: $errBody")
                    syncQueueDao.updateItem(
                        item.copy(
                            intentos = item.intentos + 1,
                            ultimo_error = "HTTP ${response.code}: $errBody"
                        )
                    )
                }
            } catch (e: Exception) {
                SyncLogCollector.w(TAG, "Fallo al enviar elemento de cola ${item.descripcion}: ${e.message}")
                syncQueueDao.updateItem(
                    item.copy(
                        intentos = item.intentos + 1,
                        ultimo_error = e.localizedMessage
                    )
                )
                // Stop queue loop if connection failed completely
                break
            }
        }
        SyncLogCollector.i(TAG, "Procesamiento de cola finalizado. Exitosos: $successful/${pending.size}")
        return@withContext successful
    }

    /**
     * Pulls latest data from the server for the user and active home,
     * updating local Room SQLite database for offline consistency.
     */
    suspend fun syncWithServer(activeHomeId: Int?): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        SyncLogCollector.i(TAG, "=== Iniciando sincronización con el servidor REST ===")
        
        // 1. Process any pending offline mutations first
        val flushed = processPendingQueue()
        
        // 2. If no token, test connectivity
        val token = ApiClient.getAuthToken()
        if (token.isNullOrBlank()) {
            val (connOk, connMsg) = testApiConnection()
            if (!connOk) {
                return@withContext Pair(false, connMsg)
            }
            return@withContext Pair(true, "Conexión activa con el servidor. Inicia sesión para sincronizar tu hogar.")
        }

        try {
            // 3. Pull Current User Profile & Homes
            SyncLogCollector.d(TAG, "Obteniendo perfil del usuario y membresías...")
            val meResp = ApiClient.service.getMe()
            if (meResp.isSuccessful && meResp.body()?.data != null) {
                val profile = meResp.body()!!.data
                userDao.insertUser(
                    UserEntity(
                        id = profile.id,
                        nombre = profile.nombre,
                        email = profile.email,
                        username = profile.username,
                        password_hash = "",
                        codigo_usuario = profile.codigo_usuario,
                        rol_global = profile.rol_global,
                        activo = profile.activo
                    )
                )

                // Sync User's Homes
                for (h in profile.hogares) {
                    homeDao.insertHome(
                        HomeEntity(
                            id = h.hogar_id,
                            nombre = h.hogar_nombre,
                            tipo_hogar = h.tipo_hogar,
                            codigo_invitacion = h.codigo_invitacion,
                            creado_por = profile.id
                        )
                    )
                    homeMemberDao.insertMember(
                        HomeMemberEntity(
                            id = h.miembro_id,
                            hogar_id = h.hogar_id,
                            usuario_id = profile.id,
                            rol = h.rol,
                            activo = true
                        )
                    )
                }
            }

            // 4. If active home is selected, pull all its entities
            if (activeHomeId != null && activeHomeId > 0) {
                SyncLogCollector.d(TAG, "Sincronizando entidades del hogar activo #$activeHomeId...")

                // A. Members
                val membersResp = ApiClient.service.getHomeMembers(activeHomeId)
                if (membersResp.isSuccessful && membersResp.body()?.data != null) {
                    val members = membersResp.body()!!.data
                    for (m in members) {
                        userDao.insertUser(
                            UserEntity(
                                id = m.usuario_id,
                                nombre = m.nombre,
                                email = m.email,
                                username = m.username,
                                password_hash = "",
                                codigo_usuario = m.codigo_usuario,
                                activo = true
                            )
                        )
                        homeMemberDao.insertMember(
                            HomeMemberEntity(
                                id = m.id,
                                hogar_id = activeHomeId,
                                usuario_id = m.usuario_id,
                                rol = m.rol,
                                activo = true
                            )
                        )
                    }
                }

                // B. Tasks & Occurrences
                val tasksResp = ApiClient.service.getTasks(activeHomeId)
                if (tasksResp.isSuccessful && tasksResp.body()?.data != null) {
                    val tasks = tasksResp.body()!!.data
                    for (t in tasks) {
                        taskDao.insertTask(
                            TaskEntity(
                                id = t.id,
                                hogar_id = t.hogar_id,
                                creado_por = t.creador?.id ?: 1,
                                titulo = t.titulo,
                                descripcion = t.descripcion,
                                tipo = t.tipo,
                                fecha_inicio = t.fecha_inicio,
                                fecha_fin = t.fecha_fin,
                                activa = t.activa
                            )
                        )
                        for (s in t.horarios) {
                            taskDao.insertSchedule(
                                TaskScheduleEntity(
                                    id = s.id ?: 0,
                                    tarea_id = t.id,
                                    hora_inicio = s.hora_inicio,
                                    hora_fin = s.hora_fin
                                )
                            )
                        }
                        for (a in t.asignados) {
                            taskDao.insertAssignment(
                                TaskAssignmentEntity(
                                    tarea_id = t.id,
                                    usuario_id = a.id
                                )
                            )
                        }
                    }
                }

                // Task Occurrences
                val occResp = ApiClient.service.getTaskOccurrences(activeHomeId)
                if (occResp.isSuccessful && occResp.body()?.data != null) {
                    val occs = occResp.body()!!.data
                    for (o in occs) {
                        occurrenceDao.insertOccurrence(
                            TaskOccurrenceEntity(
                                id = o.id,
                                tarea_id = o.tarea_id,
                                fecha = o.fecha,
                                hora_inicio = o.hora_inicio,
                                hora_fin = o.hora_fin,
                                completada = o.completada,
                                completada_por = o.completada_por?.id,
                                notas = o.notas
                            )
                        )
                    }
                }

                // C. Penalties
                val penResp = ApiClient.service.getPenalties(activeHomeId)
                if (penResp.isSuccessful && penResp.body()?.data != null) {
                    val pens = penResp.body()!!.data
                    for (p in pens) {
                        penaltyDao.insertPenalty(
                            PenaltyEntity(
                                id = p.id,
                                hogar_id = p.hogar_id,
                                creado_por = p.creador?.id ?: 1,
                                titulo = p.titulo,
                                descripcion_motivo = p.descripcion_motivo,
                                penalizacion = p.penalizacion,
                                fecha_inicio = p.fecha_inicio,
                                fecha_fin = p.fecha_fin,
                                estado = p.estado
                            )
                        )
                        for (u in p.usuarios_penalizados) {
                            penaltyDao.insertPenaltyUser(
                                PenaltyUserEntity(
                                    penalizacion_id = p.id,
                                    usuario_id = u.id
                                )
                            )
                        }
                    }
                }

                // D. Problems & Conflicts
                val probResp = ApiClient.service.getProblems(activeHomeId)
                if (probResp.isSuccessful && probResp.body()?.data != null) {
                    val probs = probResp.body()!!.data
                    for (pr in probs) {
                        problemDao.insertProblem(
                            ProblemEntity(
                                id = pr.id,
                                hogar_id = pr.hogar_id,
                                creado_por = pr.creador?.id ?: 1,
                                titulo = pr.titulo,
                                descripcion = pr.descripcion,
                                hablado = pr.hablado,
                                solucionado = pr.solucionado,
                                acuerdo = pr.acuerdo
                            )
                        )
                        for (u in pr.usuarios_involucrados) {
                            problemDao.insertProblemUser(
                                ProblemUserEntity(
                                    problema_id = pr.id,
                                    usuario_id = u.id
                                )
                            )
                        }
                        for (pov in pr.puntos_vista) {
                            problemDao.insertPointOfView(
                                ProblemPointOfViewEntity(
                                    id = pov.id,
                                    problema_id = pr.id,
                                    usuario_id = pov.usuario_id,
                                    contenido = pov.contenido
                                )
                            )
                        }
                    }
                }

                // E. Needs Lists & Items
                val needsResp = ApiClient.service.getNeedLists(activeHomeId)
                if (needsResp.isSuccessful && needsResp.body()?.data != null) {
                    val lists = needsResp.body()!!.data
                    for (l in lists) {
                        needListDao.insertList(
                            NeedListEntity(
                                id = l.id,
                                hogar_id = l.hogar_id,
                                creado_por = l.creador?.id ?: 1,
                                titulo = l.titulo,
                                estado = l.estado
                            )
                        )
                        for (item in l.items) {
                            needListDao.insertItem(
                                NeedItemEntity(
                                    id = item.id,
                                    lista_id = l.id,
                                    producto_nombre = item.producto_nombre,
                                    cantidad = item.cantidad,
                                    fecha_necesaria = item.fecha_necesaria,
                                    comprador_id = item.comprador?.id,
                                    estado = item.estado,
                                    notas = item.notas
                                )
                            )
                        }
                    }
                }

                // F. Goals & Compounds
                val goalsResp = ApiClient.service.getGoals(activeHomeId)
                if (goalsResp.isSuccessful && goalsResp.body()?.data != null) {
                    val goals = goalsResp.body()!!.data
                    for (g in goals) {
                        goalDao.insertGoal(
                            GoalEntity(
                                id = g.id,
                                hogar_id = g.hogar_id,
                                creado_por = g.creador?.id ?: 1,
                                titulo = g.titulo,
                                descripcion = g.descripcion,
                                tipo = g.tipo,
                                completado = g.completado,
                                fecha_limite_original = g.fecha_limite_original,
                                fecha_limite_actual = g.fecha_limite_actual
                            )
                        )
                        for (pt in g.puntos) {
                            goalDao.insertGoalPoint(
                                GoalPointEntity(
                                    id = pt.id,
                                    objetivo_id = g.id,
                                    titulo = pt.titulo,
                                    descripcion = pt.descripcion,
                                    asignado_a = pt.asignado?.id,
                                    completado = pt.completado,
                                    completado_por = pt.completado_por?.id,
                                    orden = pt.orden
                                )
                            )
                        }
                    }
                }
            }

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            SyncLogCollector.s(TAG, "Sincronización completa con el servidor REST completada con éxito ($time).")
            Pair(true, "Datos sincronizados correctamente con los servicios REST ($time)")
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error durante la sincronización: ${e.message}", e)
            Pair(false, "Sincronización parcial: ${e.localizedMessage}")
        }
    }
}
