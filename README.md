# 自定义优先级线程池


自定义线程池支持以下功能：

1. 支持按任务的优先级去执行；

2. 支持线程池暂停.恢复；

3. 异步结果主动回调主线程



## **ThreadPoolExecutor 基础知识**

我们自己来自定义一个线程池，今天来学习一下ThreadPoolExecutor，然后结合使用场景定义一个按照线程优先级来执行的任务的线程池。

​    ThreadPoolExecutor线程池用于管理线程任务队列、若干个线程。

##### **1.）ThreadPoolExecutor构造函数**

```Java
ThreadPoolExecutor(int corePoolSize,int maximumPoolSize,long keepAliveTime,TimeUnit unit,BlockingQueue workQueue)
ThreadPoolExecutor(int corePoolSize, int maximumPoolSize,long keepAliveTime, TimeUnit unit,BlockingQueue workQueue,RejectedExecutionHandler handler)
ThreadPoolExecutor(int corePoolSize,int maximumPoolSize,long keepAliveTime,TimeUnit unit,BlockingQueue workQueue,RejectedExecutionHandler handler) 
ThreadPoolExecutor(int corePoolSize,int maximumPoolSize,long keepAliveTime,TimeUnit unit,BlockingQueue workQueue,ThreadFactory threadFactory, RejectedExecutionHandler handler)
```

- 　　corePoolSize： 线程池维护线程的最少数量
- 　　maximumPoolSize：线程池维护线程的最大数量
- 　　keepAliveTime： 线程池维护线程所允许的空闲时间
- 　　unit： 线程池维护线程所允许的空闲时间的单位
- 　　workQueue： 线程池所使用的缓冲队列
- 　　threadFactory：线程池用于创建线程
- 　　handler： 线程池对拒绝任务的处理策略

##### **2.）创建新线程**

默认使用Executors.defaultThreadFactory()，也可以通过如下方式

```Java
    /**
     * 创建线程工厂
     */
    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "download#" + mCount.getAndIncrement());
        }
    };
```

##### **3.）线程创建规则**

​     ThreadPoolExecutor对象初始化时，不创建任何执行线程，当有新任务进来时，才会创建执行线程。构造ThreadPoolExecutor对象时，需要配置该对象的核心线程池大小和最大线程池大小

1. 当目前执行线程的总数小于核心线程大小时，所有新加入的任务，都在新线程中处理。

2. 当目前执行线程的总数大于或等于核心线程时，所有新加入的任务，都放入任务缓存队列中。

3. 当目前执行线程的总数大于或等于核心线程，并且缓存队列已满，同时此时线程总数小于线程池的最大大小，那么创建新线程，加入线程池中，协助处理新的任务。

4. 当所有线程都在执行，线程池大小已经达到上限，并且缓存队列已满时，就rejectHandler拒绝新的任务。

##### **4.）默认的RejectExecutionHandler拒绝执行策略**

1. AbortPolicy 直接丢弃新任务，并抛出RejectedExecutionException通知调用者，任务被丢弃

2. CallerRunsPolicy 用调用者的线程，执行新的任务，如果任务执行是有严格次序的，请不要使用此policy

3. DiscardPolicy 静默丢弃任务，不通知调用者，在处理网络报文时，可以使用此任务，静默丢弃没有几乎处理的报文

4. DiscardOldestPolicy 丢弃最旧的任务，处理网络报文时，可以使用此任务，因为报文处理是有时效的，超过时效的，都必须丢弃

我们也可以写一些自己的RejectedExecutionHandler，例如拒绝时，直接将线程加入缓存队列，并阻塞调用者，或根据任务的时间戳，丢弃超过限制的任务。

##### **5.）任务队列BlockingQueue**

​    排队原则

1. 如果运行的线程少于 corePoolSize，则 Executor 始终首选添加新的线程，而不进行排队。

2. 如果运行的线程等于或多于 corePoolSize，则 Executor 始终首选将请求加入队列，而不添加新的线程。

3. 如果无法将请求加入队列，则创建新的线程，除非创建此线程超出 maximumPoolSize，在这种情况下，任务将被拒绝。

常见几种BlockingQueue实现

​     1. ArrayBlockingQueue :  有界的数组队列

2. LinkedBlockingQueue : 可支持有界/无界的队列，使用链表实现

3. PriorityBlockingQueue : 优先队列，可以针对任务排序

4. SynchronousQueue : 队列长度为1的队列，和Array有点区别就是：client thread提交到block queue会是一个阻塞过程，直到有一个worker thread连接上来poll task。

##### **6.)线程池执行**

execute()方法中，调用了三个私有方法

addIfUnderCorePoolSize()：在线程池大小小于核心线程池大小的情况下，扩展线程池

addIfUnderMaximumPoolSize()：在线程池大小小于线程池大小上限的情况下，扩展线程池

ensureQueuedTaskHandled()：保证在线程池关闭的情况下，新加入队列的线程也能正确处理

##### **7.）线程池关闭**

shutdown()：不会立即终止线程池，而是要等所有任务缓存队列中的任务都执行完后才终止，但再也不会接受新的任务

shutdownNow()：立即终止线程池，并尝试打断正在执行的任务，并且清空任务缓存队列，返回尚未执行的任务



## **实现优先级线程池**

#####  **1.）两种线程任务**：

1. 任务执行完有返回值的
2. 任务执行完无返回值

```kotlin
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

```



##### **2.）自定义PriorityExecutor继承ThreadPoolExecutor**

以下代码中，有以下三点可以讲的：

1. 优先级线程池是通过设置ThreadPoolExecutor 的 BlockingQueue 参数为 PriorityBlockingQueue 来实现的，它通过比较优先级。但是不能保证优先级高的最终就一定先执行
2. 在beforeExecute 方法中，如果已经设置了暂停（isPaused），新的任务将不会被执行
3. afterExecute方法中，可以加入监控代码，可以监控用时、当前线程数等

```kotlin
// 位于 HiExecutor 的init代码
    init {
        pauseCondition = lock.newCondition()

        val cpuCount = Runtime.getRuntime().availableProcessors()
        val corePoolSize = cpuCount + 1
        val maxPoolSize = cpuCount * 2 + 1

        val FIFO: Comparator<*> = Comparator<Any?> { lhs, rhs ->
            if (lhs is PriorityRunnable &&
                rhs is PriorityRunnable) {
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

```



##### **4.）测试程序**

```kotlin
    private fun testThreadPool(){
        for (i in 1..10) {
            HiExecutor.execute(1, Runnable {
                Log.i(TAG, "priority 1 sleep start")
                Thread.sleep(100)
                Log.i(TAG, "priority 1 sleep end")
            })

            HiExecutor.execute(10, Runnable {
                Log.i(TAG, "priority 10 sleep start")
                Thread.sleep(100)
                Log.i(TAG, "priority 10 sleep end")
            })
        }
    }
```

运行结果：不难发现优先级高基本上优先执行了 最后执行的基本上优先级比较低



## 异步结果主动回调主线程

如果需要异步结果主动回调主线程，需要用户自己继承Callback接口，并在完成onBackground（子线程执行） 和 onCompleted （最终主线程执行）方法

```Java
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
```