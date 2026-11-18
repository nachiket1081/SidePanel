package com.sidepanel.app.data.model

import com.sidepanel.app.data.protocol.MetricsStreamPayload
import java.util.concurrent.ConcurrentLinkedDeque

data class PerformanceHistoryPoint(
    val timestamp: Long,
    val cpuPercent: Float,
    val gpuPercent: Float,
    val ramPercent: Float,
    val downloadBytesPerSec: Long,
    val uploadBytesPerSec: Long
)

/**
 * Thread-safe sliding window buffer retaining up to [maxCapacity] telemetry snapshots.
 * Default is 60 data points (e.g. 60 seconds at 1 Hz).
 */
class PerformanceHistoryBuffer(val maxCapacity: Int = 60) {

    private val deque = ConcurrentLinkedDeque<PerformanceHistoryPoint>()

    fun push(payload: MetricsStreamPayload) {
        val point = PerformanceHistoryPoint(
            timestamp = System.currentTimeMillis(),
            cpuPercent = payload.cpu.usagePercent.coerceIn(0f, 100f),
            gpuPercent = payload.gpu.usagePercent.coerceIn(0f, 100f),
            ramPercent = payload.ram.usagePercent.coerceIn(0f, 100f),
            downloadBytesPerSec = payload.network.downloadBytesPerSec.coerceAtLeast(0),
            uploadBytesPerSec = payload.network.uploadBytesPerSec.coerceAtLeast(0)
        )
        deque.addLast(point)
        while (deque.size > maxCapacity) {
            deque.pollFirst()
        }
    }

    fun getSnapshot(): List<PerformanceHistoryPoint> {
        return deque.toList()
    }

    fun clear() {
        deque.clear()
    }
}
