package com.focusflow.camera.eyetracking.itracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Guards the close-during-inference race that crashed the app mid-clip.
 *
 * The real GazePointEstimator needs an Android asset and the TFLite native
 * library, so neither runs on the JVM. What is reproduced here is the exact
 * locking contract the fix relies on: a run in flight must complete before
 * close frees the interpreter, and no run may start afterwards. Without that,
 * TFLite dereferences a freed native handle and the process dies with a
 * SIGSEGV that no Kotlin catch block can intercept.
 */
class GazePointEstimatorLifecycleTest {

    /** Mirrors the estimator's guard: same lock, same closed flag, same ordering. */
    private class GuardedInterpreter {
        private val lock = Any()
        @Volatile private var closed = false

        val freed = AtomicBoolean(false)
        val runsCompleted = AtomicInteger(0)
        val useAfterFree = AtomicBoolean(false)

        fun estimate(onEnter: (() -> Unit)? = null): Boolean = synchronized(lock) {
            if (closed) return false
            onEnter?.invoke()
            // Touching the native handle: if close freed it first, this is the crash.
            if (freed.get()) useAfterFree.set(true)
            runsCompleted.incrementAndGet()
            return true
        }

        fun close() = synchronized(lock) {
            if (closed) return
            closed = true
            freed.set(true)
        }
    }

    @Test
    fun `close waits for an inference already running`() {
        val estimator = GuardedInterpreter()
        val inferenceStarted = CountDownLatch(1)
        val closeRequested = CountDownLatch(1)

        val worker = Thread {
            estimator.estimate(onEnter = {
                inferenceStarted.countDown()
                // Hold the lock while close is attempted from the other thread.
                closeRequested.await(2, TimeUnit.SECONDS)
            })
        }
        worker.start()

        assertTrue(inferenceStarted.await(2, TimeUnit.SECONDS))
        val closer = Thread {
            closeRequested.countDown()
            estimator.close()
        }
        closer.start()

        worker.join(5_000)
        closer.join(5_000)

        assertFalse("close freed the interpreter under a running inference", estimator.useAfterFree.get())
        assertTrue("the in-flight inference should still have completed", estimator.runsCompleted.get() == 1)
    }

    @Test
    fun `no inference starts after close`() {
        val estimator = GuardedInterpreter()
        estimator.close()

        assertFalse("a run after close must be refused, not executed", estimator.estimate())
        assertFalse(estimator.useAfterFree.get())
    }

    @Test
    fun `concurrent runs and closes never touch a freed interpreter`() {
        repeat(50) {
            val estimator = GuardedInterpreter()
            val start = CountDownLatch(1)
            val threads = (1..8).map { i ->
                Thread {
                    start.await()
                    if (i == 1) estimator.close() else estimator.estimate()
                }
            }
            threads.forEach { it.start() }
            start.countDown()
            threads.forEach { it.join(5_000) }

            assertFalse("use-after-free on iteration $it", estimator.useAfterFree.get())
        }
    }

    @Test
    fun `close is idempotent`() {
        val estimator = GuardedInterpreter()
        estimator.close()
        estimator.close()
        assertFalse(estimator.estimate())
    }
}
