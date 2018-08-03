package com.devoteam.srit.xmlloader.http2.util;


import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.BasicFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.command.ExecutionCommand;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.support.BasicClientExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.GracefullyCloseable;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Asserts;

/**
 * Client endpoint that can be used to initiate HTTP message exchanges.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public final class ClientSessionEndpoint implements GracefullyCloseable {

    private final IOSession ioSession;
    private final AtomicBoolean closed;

    public ClientSessionEndpoint(final IOSession ioSession) {
        super();
        this.ioSession = ioSession;
        this.closed = new AtomicBoolean(false);
    }

    public void execute(final Command command) {
        ioSession.addLast(command);
        if (ioSession.isClosed()) {
            command.cancel();
        }
    }

    public void execute(
            final AsyncClientExchangeHandler exchangeHandler,
            final HttpContext context) {
        Asserts.check(!closed.get(), "Connection is already closed");
        final Command executionCommand = new ExecutionCommand(exchangeHandler, context);
        execute(executionCommand);
    }

    public <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HttpContext context,
            final FutureCallback<T> callback) {
        Asserts.check(!closed.get(), "Connection is already closed");
        final BasicFuture<T> future = new BasicFuture<>(callback);
        execute(new BasicClientExchangeHandler<>(requestProducer, responseConsumer,
                new FutureCallback<T>() {

                    @Override
                    public void completed(final T result) {
                        future.completed(result);
                    }

                    @Override
                    public void failed(final Exception ex) {
                        future.failed(ex);
                    }

                    @Override
                    public void cancelled() {
                        future.cancel();
                    }

                }),
                context);
        return future;
    }

    public <T> Future<T> execute(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final FutureCallback<T> callback) {
        return execute(requestProducer, responseConsumer, null, callback);
    }

    public boolean isOpen() {
        return !closed.get() && !ioSession.isClosed();
    }

    @Override
    public void shutdown(final ShutdownType shutdownType) {
        if (closed.compareAndSet(false, true)) {
            if (shutdownType == ShutdownType.GRACEFUL) {
                ioSession.addFirst(new ShutdownCommand(ShutdownType.GRACEFUL));
            } else {
                ioSession.shutdown(shutdownType);
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            ioSession.addFirst(new ShutdownCommand(ShutdownType.GRACEFUL));
        }
    }

    @Override
    public String toString() {
        return ioSession.toString();
    }

}