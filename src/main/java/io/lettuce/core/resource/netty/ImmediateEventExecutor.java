package io.lettuce.core.resource.netty;

import io.netty.util.concurrent.*;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

public final class ImmediateEventExecutor extends AbstractEventExecutor {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ImmediateEventExecutor.class);
    public static final ImmediateEventExecutor INSTANCE = new ImmediateEventExecutor();
    private static final FastThreadLocal<Queue<Runnable>> DELAYED_RUNNABLES = new FastThreadLocal<Queue<Runnable>>() {
        protected Queue<Runnable> initialValue() throws Exception {
            return new ArrayDeque<>();
        }
    };
    private static final FastThreadLocal<Boolean> RUNNING = new FastThreadLocal<Boolean>() {
        protected Boolean initialValue() throws Exception {
            return false;
        }
    };
    private final Future<?> terminationFuture;

    private ImmediateEventExecutor() {
        this.terminationFuture = new FailedFuture<>(GlobalEventExecutor.INSTANCE, new UnsupportedOperationException());
    }

    public boolean inEventLoop() {
        return true;
    }

    public boolean inEventLoop(Thread thread) {
        return true;
    }

    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        return this.terminationFuture();
    }

    public Future<?> terminationFuture() {
        return this.terminationFuture;
    }

    /** @deprecated */
    @Deprecated
    public void shutdown() {
    }

    public boolean isShuttingDown() {
        return false;
    }

    public boolean isShutdown() {
        return false;
    }

    public boolean isTerminated() {
        return false;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return false;
    }

    public void execute(Runnable command) {
        ObjectUtil.checkNotNull(command, "command");
        if (!(Boolean)RUNNING.get()) {
            RUNNING.set(true);
            boolean var14 = false;

            Queue<Runnable> delayedRunnables = null;
            Runnable runnable = null;
            label132: {
                try {
                    var14 = true;
                    command.run();
                    var14 = false;
                    break label132;
                } catch (Throwable var18) {
                    logger.info("Throwable caught while executing Runnable {}", command, var18);
                    var14 = false;
                } finally {
                    if (var14) {
                        delayedRunnables = (Queue<Runnable>) DELAYED_RUNNABLES.get();

                        while((runnable = (Runnable) delayedRunnables.poll()) != null) {
                            try {
                                runnable.run();
                            } catch (Throwable var15) {
                                logger.info("Throwable caught while executing Runnable {}", runnable, var15);
                            }
                        }

                        RUNNING.set(false);
                    }
                }

                delayedRunnables = (Queue<Runnable>) DELAYED_RUNNABLES.get();

                while((runnable = (Runnable)delayedRunnables.poll()) != null) {
                    try {
                        runnable.run();
                    } catch (Throwable var16) {
                        logger.info("Throwable caught while executing Runnable {}", runnable, var16);
                    }
                }

                RUNNING.set(false);
                return;
            }

            delayedRunnables = (Queue<Runnable>) DELAYED_RUNNABLES.get();

            while((runnable = (Runnable)delayedRunnables.poll()) != null) {
                try {
                    runnable.run();
                } catch (Throwable var17) {
                    logger.info("Throwable caught while executing Runnable {}", runnable, var17);
                }
            }

            RUNNING.set(false);
        } else {
            ((Queue<Runnable>) DELAYED_RUNNABLES.get()).add(command);
        }

    }

    public <V> Promise<V> newPromise() {
        return new ImmediateEventExecutor.ImmediatePromise<>(this);
    }

    public <V> ProgressivePromise<V> newProgressivePromise() {
        return new ImmediateEventExecutor.ImmediateProgressivePromise<>(this);
    }

    static class ImmediateProgressivePromise<V> extends DefaultProgressivePromise<V> {
        ImmediateProgressivePromise(EventExecutor executor) {
            super(executor);
        }

        protected void checkDeadLock() {
        }
    }

    static class ImmediatePromise<V> extends DefaultPromise<V> {
        ImmediatePromise(EventExecutor executor) {
            super(executor);
        }

        protected void checkDeadLock() {
        }
    }
}
