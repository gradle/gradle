/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.launcher.daemon.management.internal;

import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.remote.Address;
import org.gradle.internal.remote.internal.RemoteConnection;
import org.gradle.internal.remote.internal.inet.TcpOutgoingConnector;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.Serializers;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer;
import org.gradle.launcher.daemon.protocol.Failure;
import org.gradle.launcher.daemon.protocol.Finished;
import org.gradle.launcher.daemon.protocol.Message;
import org.gradle.launcher.daemon.protocol.Result;
import org.gradle.launcher.daemon.protocol.Success;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sends commands to a daemon of any Gradle version.
 *
 * <p>Two version dependent details are handled here.
 *
 * <p>The first is the connection preamble. Gradle 9.3 added a fixed greeting that a client writes on
 * connecting, and daemons from that version on refuse a connection without it. Older daemons read those
 * bytes as a message and fail. The version of the daemon is known before connecting, so the preamble is
 * sent only where it is expected.
 *
 * <p>The second is the reply. Commands such as {@code Stop} travel through the Java serialization
 * fallback, whose tag is a constant, so they are understood by every version since 7.0. Replies do not:
 * {@code Success} and {@code Failure} have explicit serializers whose tags depend on how many other
 * serializers that Gradle version registered, and that number has changed. A reply can therefore only be
 * read when the daemon runs this same version. Against an older daemon the command is sent and the
 * connection closed, and the effect is confirmed by observing the daemon instead of by reading an answer.
 *
 * <p>Every wait for a reply is bounded, whichever version is answering. A daemon that has stopped reading
 * still holds its port, so the connection succeeds and nothing ever comes back.
 */
public class DaemonControlChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger(DaemonControlChannel.class);

    /**
     * The first version that expects the connection preamble.
     */
    private static final GradleVersion PREAMBLE_FROM = GradleVersion.version("9.3");

    /**
     * How long to wait for a daemon of another Gradle version to answer a command before giving up on it.
     */
    private static final long REPLY_TIMEOUT_MILLIS = 10000;

    private final TcpOutgoingConnector connector = new TcpOutgoingConnector();

    /**
     * Whether the daemon at this address accepts connections, which is the cheapest evidence that it is
     * alive and that the entry describing it is not stale.
     */
    public boolean isReachable(Address address, GradleVersion daemonVersion) {
        RemoteConnection<Message> connection = tryConnect(address, daemonVersion);
        if (connection == null) {
            return false;
        }
        closeQuietly(connection);
        return true;
    }

    /**
     * Sends a command and, where the daemon runs this same Gradle version, waits for its answer.
     *
     * @return the answer when one could be read, otherwise {@link Reply#sent()} when the command was
     * written but no answer could be understood, or {@link Reply#failed()} when it could not be sent
     */
    public Reply send(Address address, GradleVersion daemonVersion, Command command) {
        RemoteConnection<Message> connection = tryConnect(address, daemonVersion);
        if (connection == null) {
            return Reply.failed();
        }
        boolean written = false;
        try {
            connection.dispatch(command);
            connection.flush();
            written = true;
            // The reply has to be waited for, whatever version answers it. Closing straight after writing
            // loses the command often enough to matter: the daemon answers every command it accepts, so an
            // answer arriving is what proves the command was read. The wait is bounded because a daemon
            // that has stopped reading must not hold up a command that is meant to stop it.
            Answer answer = awaitReply(connection);
            if (!answer.arrived) {
                LOGGER.debug("The Gradle {} daemon at {} did not answer {} within {}ms.",
                    daemonVersion.getVersion(), address, command, REPLY_TIMEOUT_MILLIS);
                // Reporting this as delivered would leave a wedged daemon running, because the caller
                // falls back to the process only for a command it believes never arrived.
                return Reply.failed();
            }
            if (!isCurrentVersion(daemonVersion) || !(answer.message instanceof Result)) {
                // Replies cannot be decoded across versions, so the effect is confirmed by observing the
                // daemon rather than by reading what came back.
                return Reply.sent();
            }
            Result<?> result = (Result<?>) answer.message;
            connection.dispatch(new Finished());
            connection.flush();
            if (result instanceof Failure) {
                return Reply.refused(((Failure) result).getValue());
            }
            if (result instanceof Success) {
                return Reply.answered(result.getValue());
            }
            return Reply.sent();
        } catch (Throwable e) {
            LOGGER.debug("Could not complete {} against the Gradle {} daemon at {}.", command, daemonVersion.getVersion(), address, e);
            // A command that was never written must not be reported as delivered, or the caller will wait
            // for an effect instead of falling back to the process.
            return written ? Reply.sent() : Reply.failed();
        } finally {
            closeQuietly(connection);
        }
    }

    /**
     * Waits for the daemon to send something back, on a thread of its own.
     *
     * <p>{@link RemoteConnection#receive()} has no timeout, and a daemon that has stopped reading still
     * holds its port, so the connection succeeds and the call never returns. Reading on another thread
     * and giving up on it is the only bound available.
     *
     * <p>A reply that cannot be decoded still counts as having arrived. Across versions that is the
     * normal case, and it is the arrival rather than the content that shows the command was read.
     */
    private static Answer awaitReply(final RemoteConnection<Message> connection) {
        final AtomicReference<Message> received = new AtomicReference<Message>();
        final AtomicBoolean arrived = new AtomicBoolean();
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    received.set(connection.receive());
                } catch (Throwable ignored) {
                    // Bytes arrived and could not be decoded, which still means the daemon read the command.
                }
                arrived.set(true);
            }
        }, "Gradle daemon management reply");
        reader.setDaemon(true);
        reader.start();
        try {
            reader.join(REPLY_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new Answer(arrived.get(), received.get());
    }

    /**
     * What a daemon sent back, if anything, before the wait ran out.
     */
    private static final class Answer {
        private final boolean arrived;
        private final @Nullable Message message;

        private Answer(boolean arrived, @Nullable Message message) {
            this.arrived = arrived;
            this.message = message;
        }
    }

    private @Nullable RemoteConnection<Message> tryConnect(Address address, GradleVersion daemonVersion) {
        try {
            return connector.connect(address, expectsPreamble(daemonVersion)).create(Serializers.stateful(createSerializer()));
        } catch (Exception e) {
            LOGGER.debug("Could not connect to the Gradle {} daemon at {}.", daemonVersion.getVersion(), address, e);
            return null;
        }
    }

    private static boolean expectsPreamble(GradleVersion daemonVersion) {
        return daemonVersion.getBaseVersion().compareTo(PREAMBLE_FROM) >= 0;
    }

    private static boolean isCurrentVersion(GradleVersion daemonVersion) {
        return GradleVersion.current().getVersion().equals(daemonVersion.getVersion());
    }

    private static Serializer<Message> createSerializer() {
        return DaemonMessageSerializer.create(new UnusableBuildActionSerializer());
    }

    private static void closeQuietly(RemoteConnection<Message> connection) {
        try {
            connection.stop();
        } catch (Exception e) {
            LOGGER.debug("Could not close the daemon connection.", e);
        }
    }

    /**
     * What came back from a daemon.
     */
    public static final class Reply {
        private final boolean delivered;
        private final boolean understood;
        private final @Nullable Object value;
        private final @Nullable Throwable failure;

        private Reply(boolean delivered, boolean understood, @Nullable Object value, @Nullable Throwable failure) {
            this.delivered = delivered;
            this.understood = understood;
            this.value = value;
            this.failure = failure;
        }

        static Reply failed() {
            return new Reply(false, false, null, null);
        }

        static Reply sent() {
            return new Reply(true, false, null, null);
        }

        static Reply answered(@Nullable Object value) {
            return new Reply(true, true, value, null);
        }

        static Reply refused(@Nullable Throwable failure) {
            return new Reply(true, true, null, failure);
        }

        /**
         * Whether the command reached the daemon. A command that was delivered has taken effect even
         * when its answer could not be read.
         */
        public boolean isDelivered() {
            return delivered;
        }

        /**
         * Whether an answer came back and could be decoded.
         */
        public boolean isUnderstood() {
            return understood;
        }

        public boolean isSuccess() {
            return delivered && failure == null;
        }

        public @Nullable Object getValue() {
            return value;
        }

        public @Nullable Throwable getFailure() {
            return failure;
        }
    }

    /**
     * Stands in for the serializer of a build request.
     *
     * <p>The message serializer wants one, and management never sends a build, so this is registered and
     * never called. Building it for real would drag the whole build action infrastructure into a library
     * whose entire purpose is to stay small enough to be version independent.
     */
    private static final class UnusableBuildActionSerializer implements Serializer<BuildAction> {
        @Override
        public BuildAction read(Decoder decoder) {
            throw new UnsupportedOperationException("This client cannot run builds, only manage daemons.");
        }

        @Override
        public void write(Encoder encoder, BuildAction value) {
            throw new UnsupportedOperationException("This client cannot run builds, only manage daemons.");
        }
    }
}
