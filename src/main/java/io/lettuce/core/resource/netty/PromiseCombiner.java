package io.lettuce.core.resource.netty;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;

public final class PromiseCombiner {
    private int expectedCount;
    private int doneCount;
    private Promise<Void> aggregatePromise;
    private Throwable cause;
    private final GenericFutureListener<Future<?>> listener;
    private final EventExecutor executor;

    /** @deprecated */
    @Deprecated
    public PromiseCombiner() {
        this(ImmediateEventExecutor.INSTANCE);
    }

    public PromiseCombiner(EventExecutor executor) {
        this.listener = new GenericFutureListener<Future<?>>() {
            public void operationComplete(final Future<?> future) {
                if (PromiseCombiner.this.executor.inEventLoop()) {
                    this.operationComplete0(future);
                } else {
                    PromiseCombiner.this.executor.execute(() -> operationComplete0(future));
                }

            }

            private void operationComplete0(Future<?> future) {
                assert PromiseCombiner.this.executor.inEventLoop();

                ++PromiseCombiner.this.doneCount;
                if (!future.isSuccess() && PromiseCombiner.this.cause == null) {
                    PromiseCombiner.this.cause = future.cause();
                }

                if (PromiseCombiner.this.doneCount == PromiseCombiner.this.expectedCount && PromiseCombiner.this.aggregatePromise != null) {
                    PromiseCombiner.this.tryPromise();
                }

            }
        };
        this.executor = ObjectUtil.checkNotNull(executor, "executor");
    }

    /** @deprecated */
    @Deprecated
    public void add(Promise promise) {
        this.add((Future)promise);
    }

    public void add(Future future) {
        this.checkAddAllowed();
        this.checkInEventLoop();
        ++this.expectedCount;
        future.addListener(this.listener);
    }

    /** @deprecated */
    @Deprecated
    public void addAll(Promise... promises) {
        this.addAll((Future[])promises);
    }

    public void addAll(Future... futures) {
        Future[] var2 = futures;
        int var3 = futures.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            Future future = var2[var4];
            this.add(future);
        }

    }

    public void finish(Promise<Void> aggregatePromise) {
        ObjectUtil.checkNotNull(aggregatePromise, "aggregatePromise");
        this.checkInEventLoop();
        if (this.aggregatePromise != null) {
            throw new IllegalStateException("Already finished");
        } else {
            this.aggregatePromise = aggregatePromise;
            if (this.doneCount == this.expectedCount) {
                this.tryPromise();
            }

        }
    }

    private void checkInEventLoop() {
        if (!this.executor.inEventLoop()) {
            throw new IllegalStateException("Must be called from EventExecutor thread");
        }
    }

    private boolean tryPromise() {
        return this.cause == null ? this.aggregatePromise.trySuccess((Void) null) : this.aggregatePromise.tryFailure(this.cause);
    }

    private void checkAddAllowed() {
        if (this.aggregatePromise != null) {
            throw new IllegalStateException("Adding promises is not allowed after finished adding");
        }
    }
}
