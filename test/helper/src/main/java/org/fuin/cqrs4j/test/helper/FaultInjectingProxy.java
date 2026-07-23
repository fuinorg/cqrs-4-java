package org.fuin.cqrs4j.test.helper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TCP forwarder that sits between the application under test and a container, and can be told to misbehave.
 * It makes a reachable service unreachable without touching the container that runs it.
 * <p>
 * The reason it exists rather than stopping the container: an application is configured with the container's
 * <em>mapped</em> port when its context starts. Stopping and starting the container hands out a different
 * mapped port, so the application would keep pointing at a dead one and could never recover - which is the
 * half of a fault-injection test that matters most. The proxy's port stays fixed for the whole test, and only
 * the forwarding behind it is switched off and on.
 *
 * <ul>
 *     <li>{@link #forward()} - normal operation (the default).</li>
 *     <li>{@link #cut()} - existing connections are dropped and new ones refused, which is what a client sees
 *         when the service goes away.</li>
 *     <li>{@link #blackhole()} - connections are accepted and bytes are swallowed, so a call is sent but
 *         never answered. This is the hang that a per-call timeout has to cut short.</li>
 * </ul>
 */
public final class FaultInjectingProxy implements AutoCloseable {

    /**
     * How the proxy treats traffic.
     */
    public enum Mode {
        /** Pass everything through. */
        FORWARD,
        /** Accept, then swallow: the request never reaches the service and no answer ever comes back. */
        BLACKHOLE,
        /** Refuse new connections and drop existing ones. */
        CUT
    }

    private final ServerSocket server;

    private final String targetHost;

    private final int targetPort;

    private final List<Socket> sockets = Collections.synchronizedList(new ArrayList<>());

    private volatile Mode mode = Mode.FORWARD;

    private volatile boolean running = true;

    private FaultInjectingProxy(final String targetHost, final int targetPort) throws IOException {
        this.server = new ServerSocket(0);
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        final Thread acceptor = new Thread(this::acceptLoop, "fault-injecting-proxy");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /**
     * Starts a proxy in front of the given target.
     *
     * @param targetHost Host to forward to.
     * @param targetPort Port to forward to.
     * @return Running proxy listening on a free port.
     * @throws IOException The listening socket could not be opened.
     */
    public static FaultInjectingProxy to(final String targetHost, final int targetPort) throws IOException {
        return new FaultInjectingProxy(targetHost, targetPort);
    }

    /**
     * Returns the port the application under test has to connect to.
     *
     * @return Local port of this proxy.
     */
    public int port() {
        return server.getLocalPort();
    }

    /**
     * Passes traffic through again and lets clients reconnect.
     */
    public void forward() {
        mode = Mode.FORWARD;
    }

    /**
     * Swallows traffic: a call is sent but never answered.
     */
    public void blackhole() {
        mode = Mode.BLACKHOLE;
    }

    /**
     * Drops all open connections and refuses new ones.
     */
    public void cut() {
        mode = Mode.CUT;
        closeOpenSockets();
    }

    private void closeOpenSockets() {
        final List<Socket> copy;
        synchronized (sockets) {
            copy = new ArrayList<>(sockets);
            sockets.clear();
        }
        for (final Socket socket : copy) {
            closeQuietly(socket);
        }
    }

    private void acceptLoop() {
        while (running) {
            final Socket client;
            try {
                client = server.accept();
            } catch (final IOException ex) {
                return;
            }
            if (mode == Mode.CUT) {
                closeQuietly(client);
                continue;
            }
            handle(client);
        }
    }

    private void handle(final Socket client) {
        Socket upstream = null;
        try {
            sockets.add(client);
            if (mode != Mode.BLACKHOLE) {
                upstream = new Socket();
                upstream.connect(new InetSocketAddress(targetHost, targetPort), 5_000);
                sockets.add(upstream);
                pump(upstream, client, "downstream");
            }
            pump(client, upstream, "upstream");
        } catch (final IOException ex) {
            closeQuietly(client);
            closeQuietly(upstream);
        }
    }

    /**
     * Copies bytes from one socket to another on a daemon thread. A {@literal null} sink or a blackholed
     * proxy makes the bytes disappear, which is what leaves the caller waiting for an answer that never
     * comes.
     *
     * @param source Socket to read from.
     * @param sink   Socket to write to, or {@literal null} to discard.
     * @param name   Thread name suffix.
     */
    private void pump(final Socket source, final Socket sink, final String name) {
        final Thread thread = new Thread(() -> {
            final byte[] buffer = new byte[8192];
            try (InputStream in = source.getInputStream()) {
                while (running) {
                    final int read = in.read(buffer);
                    if (read < 0) {
                        break;
                    }
                    if (sink == null || mode == Mode.BLACKHOLE) {
                        // Swallow - the peer waits for something that will never arrive.
                        continue;
                    }
                    final OutputStream out = sink.getOutputStream();
                    out.write(buffer, 0, read);
                    out.flush();
                }
            } catch (final IOException ex) { // NOSONAR - a dropped connection is the point of this class
                // Falls through to closing both sides
            } finally {
                closeQuietly(source);
                closeQuietly(sink);
            }
        }, "fault-injecting-proxy-" + name);
        thread.setDaemon(true);
        thread.start();
    }

    private static void closeQuietly(final Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (final IOException ex) { // NOSONAR
            // Nothing left to do
        }
    }

    @Override
    public void close() {
        running = false;
        closeOpenSockets();
        try {
            server.close();
        } catch (final IOException ex) { // NOSONAR
            // Nothing left to do
        }
    }

}
