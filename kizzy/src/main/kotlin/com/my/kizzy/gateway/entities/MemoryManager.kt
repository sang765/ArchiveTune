package com.my.kizzy.gateway.entities

import io.ktor.websocket.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.logging.Logger

/**
 * Memory manager for low-end device optimization
 * Handles memory-efficient WebSocket frame processing and cleanup
 */
class MemoryManager {
    private val logger = Logger.getLogger("MemoryManager")
    private val maxFrameSize = 16 * 1024 * 1024 // 16MB max frame size
    private val lowMemoryThreshold = 0.85 // 85% memory usage threshold
    
    /**
     * Safely read text from WebSocket frame with memory optimization
     * Uses streaming approach for large frames to prevent OOM
     */
    suspend fun readTextSafely(frame: Frame.Text): String = withContext(Dispatchers.IO) {
        try {
            val data = frame.data
            
            // Check frame size before processing
            if (data.size > maxFrameSize) {
                logger.warning("Frame size ${data.size} exceeds maximum allowed size $maxFrameSize, truncating")
                return@withContext String(data.sliceArray(0..maxFrameSize), StandardCharsets.UTF_8)
            }
            
            // For smaller frames, use direct conversion
            if (data.size < 1024 * 1024) { // 1MB
                return@withContext String(data, StandardCharsets.UTF_8)
            }
            
            // For larger frames, use streaming approach
            return@withContext readLargeFrameStreaming(data)
            
        } catch (e: OutOfMemoryError) {
            logger.severe("OutOfMemoryError while reading frame, forcing cleanup")
            forceMemoryCleanup()
            throw e
        } catch (e: Exception) {
            logger.warning("Error reading frame: ${e.message}")
            throw e
        }
    }
    
    /**
     * Read large frames using streaming approach to minimize memory usage
     */
    private fun readLargeFrameStreaming(data: ByteArray): String {
        val chunkSize = 64 * 1024 // 64KB chunks
        val output = ByteArrayOutputStream()
        
        try {
            var offset = 0
            while (offset < data.size) {
                val remainingBytes = data.size - offset
                val currentChunkSize = minOf(chunkSize, remainingBytes)
                
                output.write(data, offset, currentChunkSize)
                offset += currentChunkSize
                
                // Check memory pressure during processing
                if (isMemoryPressureHigh()) {
                    logger.warning("High memory pressure detected during frame processing")
                    System.gc() // Suggest garbage collection
                }
            }
            
            return output.toString(StandardCharsets.UTF_8.name())
        } finally {
            output.close()
        }
    }
    
    /**
     * Check if memory pressure is high and perform cleanup if needed
     */
    fun checkMemoryPressure() {
        if (isMemoryPressureHigh()) {
            logger.info("High memory pressure detected, suggesting garbage collection")
            System.gc()
        }
    }
    
    /**
     * Check if memory usage is above threshold
     */
    private fun isMemoryPressureHigh(): Boolean {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsageRatio = usedMemory.toDouble() / maxMemory.toDouble()
        
        return memoryUsageRatio > lowMemoryThreshold
    }
    
    /**
     * Force memory cleanup in emergency situations
     */
    private fun forceMemoryCleanup() {
        logger.info("Forcing memory cleanup")
        System.gc()
        System.runFinalization()
        System.gc()
    }
    
    /**
     * Cleanup resources when WebSocket is closed
     */
    fun cleanup() {
        logger.info("Cleaning up memory manager resources")
        System.gc()
    }
}
