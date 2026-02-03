package com.my.kizzy.gateway.entities

import java.util.logging.Logger

/**
 * Configuration for low-end device optimization
 * Provides memory-aware settings for Discord RPC operations
 */
class LowEndDeviceConfig {
    private val logger = Logger.getLogger("LowEndDeviceConfig")
    
    companion object {
        // Memory thresholds
        const val LOW_MEMORY_THRESHOLD_MB = 512
        const val CRITICAL_MEMORY_THRESHOLD_MB = 256
        
        // Connection timeouts (shorter for low-end devices)
        const val CONNECTION_TIMEOUT_MS = 10000L
        const val READ_TIMEOUT_MS = 15000L
        const val HEARTBEAT_TIMEOUT_MS = 30000L
        
        // Buffer sizes (smaller for memory efficiency)
        const val WEBSOCKET_BUFFER_SIZE = 8192 // 8KB
        const val MAX_MESSAGE_SIZE = 1024 * 1024 // 1MB
        
        // Retry settings
        const val MAX_RETRY_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 2000L
    }
    
    /**
     * Check if device is low-end based on available memory
     */
    fun isLowEndDevice(): Boolean {
        val runtime = Runtime.getRuntime()
        val maxMemoryMB = runtime.maxMemory() / (1024 * 1024)
        
        val isLowEnd = maxMemoryMB < LOW_MEMORY_THRESHOLD_MB
        if (isLowEnd) {
            logger.info("Low-end device detected: ${maxMemoryMB}MB max memory")
        }
        
        return isLowEnd
    }
    
    /**
     * Check if device is in critical memory state
     */
    fun isCriticalMemoryState(): Boolean {
        val runtime = Runtime.getRuntime()
        val freeMemoryMB = runtime.freeMemory() / (1024 * 1024)
        
        return freeMemoryMB < CRITICAL_MEMORY_THRESHOLD_MB
    }
    
    /**
     * Get optimized connection timeout based on device capabilities
     */
    fun getConnectionTimeout(): Long {
        return if (isLowEndDevice()) {
            CONNECTION_TIMEOUT_MS / 2 // Shorter timeout for low-end devices
        } else {
            CONNECTION_TIMEOUT_MS
        }
    }
    
    /**
     * Get optimized buffer size based on available memory
     */
    fun getOptimalBufferSize(): Int {
        return if (isCriticalMemoryState()) {
            WEBSOCKET_BUFFER_SIZE / 2 // Smaller buffer in critical state
        } else {
            WEBSOCKET_BUFFER_SIZE
        }
    }
}
