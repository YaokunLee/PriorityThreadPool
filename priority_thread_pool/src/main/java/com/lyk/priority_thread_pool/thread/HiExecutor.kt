package com.lyk.priority_thread_pool.thread

import android.os.Handler
import android.os.Looper
import androidx.annotation.IntRange
import java.util.concurrent.BlockingQueue
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

/**
 * 支持按任务的优先级去执行,
 * 支持线程池暂停.恢复(批量文件下载，上传) ，
 * 异步结果主动回调主线程
 * todo 线程池能力监控,耗时任务检测,定时,延迟,
 */
object HiExecutor {
    private const val TAG: String = "HiExecutor"
    private var isPaused: Boolean = false
    private var hiExecutor: ThreadPoolExecutor
    private var lock: ReentrantLock = ReentrantLock()
    private var pauseCondition: Condition
    private val mainHandler = Handler(Looper.getMainLooper());

    init {
        pauseCondition = lock.newCondition()

        val cpuCount = Runtime.getRuntime().availableProcessors()
        val corePoolSize = cpuCount + 1
        val maxPoolSize = cpuCount * 2 + 1

        val FIFO: Comparator<*> = Comparator<Any?> { lhs, rhs ->
            if (lhs is PriorityRunnable &&
                rhs is PriorityRunnable
            ) {
                if (lhs.priority < rhs.priority) 1 else if (lhs.priority > rhs.priority) -1 else 0
            } else {
                0
            }
        }
        val blockingQueue: PriorityBlockingQueue<out Runnable> = PriorityBlockingQueue(256, FIFO)
        val keepAliveTime = 30L
        val unit = TimeUnit.SECONDS

        val seq = AtomicLong()
        val threadFactory = ThreadFactory {
            val thread = Thread(it)
            //hi-executor-0
            thread.name = "hi-executor-" + seq.getAndIncrement()
            return@ThreadFactory thread
        }

        hiExecutor = object : ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            keepAliveTime,
            unit,
            blockingQueue as BlockingQueue<Runnable>,
            threadFactory
        ) {
            override fun beforeExecute(t: Thread?, r: Runnable?) {
                if (isPaused) {
                    lock.lock()
                    try {
                        pauseCondition.await()
                    } finally {
                        lock.unlock()
                    }
                }
            }

            override fun afterExecute(r: Runnable?, t: Throwable?) {
                //监控线程池耗时任务,线程创建数量,正在运行的数量

            }
        }
    }


    fun execute(@IntRange(from = 0, to = 10) priority: Int = 0, runnable: Runnable) {
        hiExecutor.execute(PriorityRunnable(priority, runnable))
    }

    abstract class Callable<T> : Runnable {
        override fun run() {
            mainHandler.post { onPrepare() }

            val t: T? = onBackground()

            //移除所有消息.防止需要执行onCompleted了，onPrepare还没被执行，那就不需要执行了
            mainHandler.removeCallbacksAndMessages(null)
            mainHandler.post { onCompleted(t) }
        }

        open fun onPrepare() {
            //转菊花
        }

        abstract fun onBackground(): T?
        abstract fun onCompleted(t: T?)
    }

    private class PriorityRunnable(val priority: Int, private val runnable: Runnable) : Runnable{
        override fun run() {
            runnable.run()
        }
    }


    fun pause() {
        lock.lock()
        try {
            isPaused = true

        } finally {
            lock.unlock()
        }
    }

    fun resume() {
        lock.lock()
        try {
            isPaused = false
            pauseCondition.signalAll()
        } finally {
            lock.unlock()
        }
    }
}