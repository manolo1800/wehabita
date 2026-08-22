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
        SyncLogCollector.i(TAG, "Base URL: $targetUrl")

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
            SyncLogCollector.i(TAG, "[Offline Queue] Acción encolada: $descripcion ($accion)")
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
                    SyncLogCollector.w(TAG, "Error HTTP ${response.code} sincronizando ${item.descripcion}: $errBody")
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
                // Stop queue processing on network drop
                break
            }
        }
        SyncLogCollector.i(TAG, "Procesamiento de cola finalizado. Exitosos: $successful/${pending.size}")
        return@withContext successful
    }

    private fun fetchRawJsonArray(endpoint: String): org.json.JSONArray? {
        return try {
            val client = ApiClient.getOkHttpClient()
            val baseUrl = ApiClient.getBaseUrl()
            val url = if (endpoint.startsWith("http")) endpoint else baseUrl + endpoint.removePrefix("/")
            val req = okhttp3.Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                SyncLogCollector.d(TAG, "Raw GET $endpoint responded HTTP ${resp.code}")
                return null
            }
            val bodyStr = resp.body?.string()?.trim() ?: return null
            if (bodyStr.startsWith("[")) {
                org.json.JSONArray(bodyStr)
            } else if (bodyStr.startsWith("{")) {
                val obj = org.json.JSONObject(bodyStr)
                when {
                    obj.has("data") && obj.optJSONArray("data") != null -> obj.getJSONArray("data")
                    obj.has("items") && obj.optJSONArray("items") != null -> obj.getJSONArray("items")
                    obj.has("tareas") && obj.optJSONArray("tareas") != null -> obj.getJSONArray("tareas")
                    obj.has("problemas") && obj.optJSONArray("problemas") != null -> obj.getJSONArray("problemas")
                    else -> null
                }
            } else null
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Excepción consultando raw JSON $endpoint: ${e.message}")
            null
        }
    }

    /**
     * Pulls latest data from the server for the user and active home,
     * updating local Room SQLite database for offline consistency.
     */
    suspend fun syncWithServer(activeHomeId: Int?): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = ApiClient.getAuthToken()
        SyncLogCollector.i(TAG, "=== Iniciando sincronización REST (Token presente: ${!token.isNullOrBlank()}) ===")
        
        // 1. Process any pending offline mutations first
        processPendingQueue()
        
        // 2. If no token, test connectivity
        if (token.isNullOrBlank()) {
            val (connOk, connMsg) = testApiConnection()
            if (!connOk) {
                return@withContext Pair(false, connMsg)
            }
            return@withContext Pair(true, "Conexión activa con el servidor. Inicia sesión para sincronizar tus hogares.")
        }

        try {
            var targetHomeId = activeHomeId ?: ApiClient.getSavedHomeId()

            // 3. Pull Current User Profile & Memberships
            SyncLogCollector.d(TAG, "Obteniendo perfil del usuario (GET /api/auth/me)...")
            var currentUserId = ApiClient.getSavedUserId()
            try {
                val meResp = ApiClient.service.getMe()
                if (meResp.isSuccessful && meResp.body()?.data != null) {
                    val profile = meResp.body()!!.data
                    currentUserId = profile.id
                    ApiClient.saveUserId(profile.id)

                    val existingLocalUser = userDao.getUserById(profile.id)
                    val passHash = existingLocalUser?.password_hash ?: ""

                    userDao.insertUser(
                        UserEntity(
                            id = profile.id,
                            nombre = profile.nombre,
                            email = profile.email,
                            username = profile.username,
                            password_hash = passHash,
                            codigo_usuario = profile.codigo_usuario,
                            rol_global = profile.rol_global,
                            activo = profile.activo
                        )
                    )

                    // Sync User's Homes from Profile
                    for (h in profile.hogares) {
                        if (h.hogar_nombre.isNotBlank() && h.hogar_id > 0) {
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

                    if ((targetHomeId == null || targetHomeId <= 0) && profile.hogares.isNotEmpty()) {
                        targetHomeId = profile.hogares.first().hogar_id
                        ApiClient.saveHomeId(targetHomeId)
                    }

                    SyncLogCollector.s(TAG, "Perfil de ${profile.nombre} sincronizado (${profile.hogares.size} hogares en perfil).")
                } else {
                    SyncLogCollector.w(TAG, "GET /api/auth/me respondió ${meResp.code()}: ${meResp.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                SyncLogCollector.w(TAG, "Error consultando GET /api/auth/me: ${e.message}")
            }

            // 3b. Pull Homes from GET /api/hogares (MyHomes)
            try {
                val myHomesResp = ApiClient.service.getMyHomes()
                if (myHomesResp.isSuccessful && myHomesResp.body()?.data != null) {
                    val homes = myHomesResp.body()!!.data
                    for (h in homes) {
                        if (h.hogar_nombre.isNotBlank() && h.hogar_id > 0) {
                            homeDao.insertHome(
                                HomeEntity(
                                    id = h.hogar_id,
                                    nombre = h.hogar_nombre,
                                    tipo_hogar = h.tipo_hogar,
                                    codigo_invitacion = h.codigo_invitacion,
                                    creado_por = currentUserId ?: 1
                                )
                            )
                            homeMemberDao.insertMember(
                                HomeMemberEntity(
                                    id = h.miembro_id,
                                    hogar_id = h.hogar_id,
                                    usuario_id = currentUserId ?: 1,
                                    rol = h.rol,
                                    activo = true
                                )
                            )
                        }
                    }
                    if ((targetHomeId == null || targetHomeId <= 0) && homes.isNotEmpty()) {
                        targetHomeId = homes.first().hogar_id
                        ApiClient.saveHomeId(targetHomeId)
                    }
                    SyncLogCollector.s(TAG, "Hogares obtenidos de GET /api/hogares: ${homes.size} encontrados.")
                }
            } catch (e: Exception) {
                SyncLogCollector.d(TAG, "No se pudo consultar GET /api/hogares directamente: ${e.message}")
            }

            // Fallback: Check if targetHomeId is still null, look up in homeDao
            if (targetHomeId == null || targetHomeId <= 0) {
                val localHomes = homeDao.getAllHomesList()
                if (localHomes.isNotEmpty()) {
                    targetHomeId = localHomes.first().id
                    ApiClient.saveHomeId(targetHomeId)
                }
            }

            // 4. If active home is selected, pull all its entities
            if (targetHomeId != null && targetHomeId > 0) {
                SyncLogCollector.d(TAG, "Sincronizando entidades del hogar activo #$targetHomeId...")
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                // A. Members
                try {
                    val membersResp = ApiClient.service.getHomeMembers(targetHomeId)
                    if (membersResp.isSuccessful && membersResp.body()?.data != null) {
                        val members = membersResp.body()!!.data
                        for (m in members) {
                            val exist = userDao.getUserById(m.usuario_id)
                            userDao.insertUser(
                                UserEntity(
                                    id = m.usuario_id,
                                    nombre = m.nombre,
                                    email = m.email,
                                    username = m.username,
                                    password_hash = exist?.password_hash ?: "",
                                    codigo_usuario = m.codigo_usuario,
                                    activo = true
                                )
                            )
                            homeMemberDao.insertMember(
                                HomeMemberEntity(
                                    id = m.id,
                                    hogar_id = targetHomeId,
                                    usuario_id = m.usuario_id,
                                    rol = m.rol,
                                    activo = true
                                )
                            )
                        }
                        SyncLogCollector.d(TAG, "Miembros sincronizados: ${members.size}")
                    }
                } catch (e: Exception) {
                    SyncLogCollector.w(TAG, "Error sincronizando miembros del hogar: ${e.message}")
                }

                // B. Tasks & Occurrences (with Raw Fallback)
                var tasksCount = 0
                try {
                    val tasksResp = ApiClient.service.getTasks(targetHomeId)
                    if (tasksResp.isSuccessful && tasksResp.body()?.data != null) {
                        val tasks = tasksResp.body()!!.data
                        tasksCount = tasks.size
                        for (t in tasks) {
                            taskDao.insertTask(
                                TaskEntity(
                                    id = t.id,
                                    hogar_id = targetHomeId,
                                    creado_por = t.creado_por ?: t.creador?.id ?: currentUserId ?: 1,
                                    titulo = t.titulo,
                                    descripcion = t.descripcion,
                                    tipo = t.tipo,
                                    fecha_inicio = t.fecha_inicio.ifBlank { todayStr },
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
                        SyncLogCollector.d(TAG, "Tareas sincronizadas vía Retrofit: $tasksCount")
                    }
                } catch (e: Exception) {
                    SyncLogCollector.w(TAG, "Error sincronizando tareas vía Retrofit: ${e.message}")
                }

                // Fallback direct raw JSON for tasks if needed
                if (tasksCount == 0) {
                    val rawTasks = fetchRawJsonArray("hogares/$targetHomeId/tareas")
                    if (rawTasks != null) {
                        for (i in 0 until rawTasks.length()) {
                            val obj = rawTasks.optJSONObject(i) ?: continue
                            val tId = obj.optInt("id", 0)
                            val tTitulo = obj.optString("titulo", "")
                            if (tId > 0 && tTitulo.isNotBlank()) {
                                taskDao.insertTask(
                                    TaskEntity(
                                        id = tId,
                                        hogar_id = targetHomeId,
                                        creado_por = obj.optInt("creado_por", currentUserId ?: 1),
                                        titulo = tTitulo,
                                        descripcion = if (obj.isNull("descripcion")) null else obj.optString("descripcion"),
                                        tipo = obj.optString("tipo", "unica"),
                                        fecha_inicio = obj.optString("fecha_inicio", todayStr),
                                        fecha_fin = if (obj.isNull("fecha_fin")) null else obj.optString("fecha_fin"),
                                        activa = obj.optBoolean("activa", true)
                                    )
                                )
                                tasksCount++
                            }
                        }
                        SyncLogCollector.s(TAG, "Tareas sincronizadas vía Raw JSON: $tasksCount")
                    }
                }

                // Task Occurrences
                var occsCount = 0
                try {
                    val occResp = ApiClient.service.getTaskOccurrences(targetHomeId)
                    if (occResp.isSuccessful && occResp.body()?.data != null) {
                        val occs = occResp.body()!!.data
                        occsCount = occs.size
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
                        SyncLogCollector.d(TAG, "Ocurrencias de tareas sincronizadas: $occsCount")
                    }
                } catch (e: Exception) {
                    SyncLogCollector.w(TAG, "Error sincronizando ocurrencias: ${e.message}")
                }

                // If no occurrences existed on server for local tasks, auto-generate today occurrence
                val allLocalTasks = taskDao.getAllTasksList().filter { it.hogar_id == targetHomeId }
                for (task in allLocalTasks) {
                    val existingOccs = occurrenceDao.getOccurrencesForTask(task.id)
                    if (existingOccs.isEmpty()) {
                        occurrenceDao.insertOccurrence(
                            TaskOccurrenceEntity(
                                tarea_id = task.id,
                                fecha = todayStr,
                                hora_inicio = "08:00",
                                hora_fin = null,
                                completada = false
                            )
                        )
                        if (task.fecha_inicio.isNotBlank() && task.fecha_inicio != todayStr) {
                            occurrenceDao.insertOccurrence(
                                TaskOccurrenceEntity(
                                    tarea_id = task.id,
                                    fecha = task.fecha_inicio,
                                    hora_inicio = "08:00",
                                    hora_fin = null,
                                    completada = false
                                )
                            )
                        }
                    }
                }

                // C. Penalties (with Raw Fallback)
                var pensCount = 0
                try {
                    val penResp = ApiClient.service.getPenalties(targetHomeId)
                    if (penResp.isSuccessful && penResp.body()?.data != null) {
                        val pens = penResp.body()!!.data
                        pensCount = pens.size
                        for (p in pens) {
                            penaltyDao.insertPenalty(
                                PenaltyEntity(
                                    id = p.id,
                                    hogar_id = targetHomeId,
                                    creado_por = p.creado_por ?: p.creador?.id ?: currentUserId ?: 1,
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
                        SyncLogCollector.d(TAG, "Penalizaciones sincronizadas: $pensCount")
                    }
                } catch (e: Exception) {
                    SyncLogCollector.w(TAG, "Error sincronizando penalizaciones vía Retrofit: ${e.message}")
                }

                if (pensCount == 0) {
                    val rawPens = fetchRawJsonArray("hogares/$targetHomeId/penalizaciones")
                    if (rawPens != null) {
                        for (i in 0 until rawPens.length()) {
                            val obj = rawPens.optJSONObject(i) ?: continue
                            val pId = obj.optInt("id", 0)
                            val pTitulo = obj.optString("titulo", "")
                            if (pId > 0 && pTitulo.isNotBlank()) {
                                penaltyDao.insertPenalty(
                                    PenaltyEntity(
                                        id = pId,
                                        hogar_id = targetHomeId,
                                        creado_por = obj.optInt("creado_por", currentUserId ?: 1),
                                        titulo = pTitulo,
                                        descripcion_motivo = obj.optString("descripcion_motivo", ""),
                                        penalizacion = obj.optString("penalizacion", ""),
                                        fecha_inicio = obj.optString("fecha_inicio", todayStr),
                                        fecha_fin = if (obj.isNull("fecha_fin")) null else obj.optString("fecha_fin"),
                                        estado = obj.optString("estado", "activa")
                                    )
                                )
                            }
                        }
                    }
                }

                // D. Problems & Conflicts (with Raw Fallback)
                var probsCount = 0
                try {
                    val probResp = ApiClient.service.getProblems(targetHomeId)
                    if (probResp.isSuccessful && probResp.body()?.data != null) {
                        val probs = probResp.body()!!.data
                        probsCount = probs.size
                        for (pr in probs) {
                            problemDao.insertProblem(
                                ProblemEntity(
                                    id = pr.id,
                                    hogar_id = targetHomeId,
                                    creado_por = pr.creado_por ?: pr.creador?.id ?: currentUserId ?: 1,
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
                        SyncLogCollector.d(TAG, "Problemas sincronizados vía Retrofit: $probsCount")
                    }
                } catch (e: Exception) {
                    SyncLogCollector.w(TAG, "Error sincronizando problemas vía Retrofit: ${e.message}")
                }

                if (probsCount == 0) {
                    val rawProbs = fetchRawJsonArray("hogares/$targetHomeId/problemas")
                    if (rawProbs != null) {
                        for (i in 0 until rawProbs.length()) {
                            val obj = rawProbs.optJSONObject(i) ?: continue
                            val prId = obj.optInt("id", 0)
                            val prTitulo = obj.optString("titulo", "")
                            if (prId > 0 && prTitulo.isNotBlank()) {
                                problemDao.insertProblem(
                                    ProblemEntity(
                                        id = prId,
                                        hogar_id = targetHomeId,
                                        creado_por = obj.optInt("creado_por", currentUserId ?: 1),
                                        titulo = prTitulo,
                                        descripcion = obj.optString("descripcion", ""),
                                        hablado = obj.optBoolean("hablado", false),
                                        solucionado = obj.optBoolean("solucionado", false),
                                        acuerdo = if (obj.isNull("acuerdo")) null else obj.optString("acuerdo")
                                    )
                                )
                                probsCount++
                            }
                        }
                        SyncLogCollector.s(TAG, "Problemas sincronizados vía Raw JSON: $probsCount")
                    }
                }

                // E. Needs Lists & Items (with Raw Fallback)
                var needsCount = 0
                try {
                    val needsResp = ApiClient.service.getNeedLists(targetHomeId)
                    if (needsResp.isSuccessful && needsResp.body()?.data != null) {
                        val lists = needsResp.body()!!.data
                        needsCount = lists.size
                        for (l in lists) {
                            needListDao.insertList(
                                NeedListEntity(
                                    id = l.id,
                                    hogar_id = targetHomeId,
                                    creado_por = l.creado_por ?: l.creador?.id ?: currentUserId ?: 1,
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
                        SyncLogCollector.d(TAG, "Listas de compras sincronizadas: $needsCount")
                    }
                } catch (e: Exception) {
                    SyncLogCollector.w(TAG, "Error sincronizando listas de necesidades vía Retrofit: ${e.message}")
                }

                if (needsCount == 0) {
                    val rawNeeds = fetchRawJsonArray("hogares/$targetHomeId/necesidades")
                    if (rawNeeds != null) {
                        for (i in 0 until rawNeeds.length()) {
                            val obj = rawNeeds.optJSONObject(i) ?: continue
                            val lId = obj.optInt("id", 0)
                            val lTitulo = obj.optString("titulo", "")
                            if (lId > 0 && lTitulo.isNotBlank()) {
                                needListDao.insertList(
                                    NeedListEntity(
                                        id = lId,
                                        hogar_id = targetHomeId,
                                        creado_por = obj.optInt("creado_por", currentUserId ?: 1),
                                        titulo = lTitulo,
                                        estado = obj.optString("estado", "activa")
                                    )
                                )
                            }
                        }
                    }
                }

                // F. Goals & Compound Missions (with Raw Fallback)
                var goalsCount = 0
                try {
                    val goalsResp = ApiClient.service.getGoals(targetHomeId)
                    if (goalsResp.isSuccessful && goalsResp.body()?.data != null) {
                        val goals = goalsResp.body()!!.data
                        goalsCount = goals.size
                        for (g in goals) {
                            goalDao.insertGoal(
                                GoalEntity(
                                    id = g.id,
                                    hogar_id = targetHomeId,
                                    creado_por = g.creado_por ?: g.creador?.id ?: currentUserId ?: 1,
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
                        SyncLogCollector.d(TAG, "Metas sincronizadas: $goalsCount")
                    }
                } catch (e: Exception) {
                    SyncLogCollector.w(TAG, "Error sincronizando metas vía Retrofit: ${e.message}")
                }

                if (goalsCount == 0) {
                    val rawGoals = fetchRawJsonArray("hogares/$targetHomeId/objetivos")
                    if (rawGoals != null) {
                        for (i in 0 until rawGoals.length()) {
                            val obj = rawGoals.optJSONObject(i) ?: continue
                            val gId = obj.optInt("id", 0)
                            val gTitulo = obj.optString("titulo", "")
                            if (gId > 0 && gTitulo.isNotBlank()) {
                                goalDao.insertGoal(
                                    GoalEntity(
                                        id = gId,
                                        hogar_id = targetHomeId,
                                        creado_por = obj.optInt("creado_por", currentUserId ?: 1),
                                        titulo = gTitulo,
                                        descripcion = if (obj.isNull("descripcion")) null else obj.optString("descripcion"),
                                        tipo = obj.optString("tipo", "simple"),
                                        completado = obj.optBoolean("completado", false),
                                        fecha_limite_original = obj.optString("fecha_limite_original", todayStr),
                                        fecha_limite_actual = obj.optString("fecha_limite_actual", todayStr)
                                    )
                                )
                            }
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
