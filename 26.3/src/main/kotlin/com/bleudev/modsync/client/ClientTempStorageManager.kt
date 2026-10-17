package com.bleudev.modsync.client

class ClientTempStorageManager {
    @Volatile
    var connectionAttempt: Long = 0
    var serverHost: String? = null
    var serverPort: Int? = null
    val serverAddress: String? get() {
        val h = serverHost ?: return null
        val p = serverPort ?: return null
        return "$h:$p"
    }

    @Volatile
    var downloadReadBytes: Long = 0
    @Volatile
    var downloadTotalBytes: Long? = null

    companion object {
        private var instance: ClientTempStorageManager? = null

        @JvmStatic
        fun getInstance(): ClientTempStorageManager {
            if (instance == null) {
                instance = ClientTempStorageManager()
            }
            return instance!!
        }
    }
}
