package com.example.data.repository

import com.example.data.remote.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.Socket
import java.net.URL

class RestSyncManager(
    private val repositoryProvider: () -> WeHabitaRepository?
) {
    private val TAG = "WeHabita:RestSync"

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

    suspend fun syncWithServer(activeHomeId: Int?): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val repo = repositoryProvider()
        if (repo == null) {
            return@withContext Pair(false, "Repositorio no disponible")
        }

        try {
            SyncLogCollector.i(TAG, "=== Iniciando sincronización completa desde el servidor REST ===")
            repo.fetchHomes()
            repo.fetchPrivileges()
            if (activeHomeId != null) {
                repo.fetchAllHomeData(activeHomeId)
            }
            SyncLogCollector.s(TAG, "Sincronización en memoria completada exitosamente.")
            Pair(true, "Datos actualizados desde los servicios REST")
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error en la sincronización: ${e.message}", e)
            Pair(false, "Error: ${e.message}")
        }
    }

    suspend fun syncTasks(homeId: Int): Boolean = withContext(Dispatchers.IO) {
        val repo = repositoryProvider() ?: return@withContext false
        try {
            repo.fetchTasks(homeId)
            true
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Error al sincronizar tareas: ${e.message}")
            false
        }
    }

    suspend fun syncPenalties(homeId: Int): Boolean = withContext(Dispatchers.IO) {
        val repo = repositoryProvider() ?: return@withContext false
        try {
            repo.fetchPenalties(homeId)
            true
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Error al sincronizar penalizaciones: ${e.message}")
            false
        }
    }

    suspend fun syncProblems(homeId: Int): Boolean = withContext(Dispatchers.IO) {
        val repo = repositoryProvider() ?: return@withContext false
        try {
            repo.fetchProblems(homeId)
            true
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Error al sincronizar problemas: ${e.message}")
            false
        }
    }

    suspend fun syncShopping(homeId: Int): Boolean = withContext(Dispatchers.IO) {
        val repo = repositoryProvider() ?: return@withContext false
        try {
            repo.fetchShopping(homeId)
            true
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Error al sincronizar compras: ${e.message}")
            false
        }
    }

    suspend fun syncGoals(homeId: Int): Boolean = withContext(Dispatchers.IO) {
        val repo = repositoryProvider() ?: return@withContext false
        try {
            repo.fetchGoals(homeId)
            true
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Error al sincronizar metas: ${e.message}")
            false
        }
    }

    suspend fun syncMembers(homeId: Int): Boolean = withContext(Dispatchers.IO) {
        val repo = repositoryProvider() ?: return@withContext false
        try {
            repo.fetchMembers(homeId)
            true
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Error al sincronizar miembros: ${e.message}")
            false
        }
    }

    suspend fun syncHomes(): Boolean = withContext(Dispatchers.IO) {
        val repo = repositoryProvider() ?: return@withContext false
        try {
            repo.fetchHomes()
            true
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Error al sincronizar hogares: ${e.message}")
            false
        }
    }

    suspend fun processPendingQueue(): Int {
        // En modo directo sin SQLite no hay cola diferida, todas las mutaciones van directamente al servicio
        return 0
    }
}
