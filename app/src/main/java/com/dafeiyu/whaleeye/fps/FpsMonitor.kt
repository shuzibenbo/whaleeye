package com.dafeiyu.whaleeye.fps

import android.os.SystemClock
import android.view.Choreographer

class FpsMonitor(private val onFps: (Float) -> Unit) : Choreographer.FrameCallback {

    private val choreographer: Choreographer = Choreographer.getInstance()
    private var frames = 0
    private var lastTs = 0L
    private var running = false

    fun start() {
        if (running) return
        running = true
        frames = 0
        lastTs = 0L
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        if (!running) return
        running = false
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        frames++
        val now = SystemClock.uptimeMillis()
        if (lastTs == 0L) lastTs = now
        val elapsed = now - lastTs
        if (elapsed >= 500) {
            onFps(frames * 1000f / elapsed)
            frames = 0
            lastTs = now
        }
        choreographer.postFrameCallback(this)
    }
}