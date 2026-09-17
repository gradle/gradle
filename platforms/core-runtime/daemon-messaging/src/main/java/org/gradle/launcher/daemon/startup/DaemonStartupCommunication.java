/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.daemon.startup;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.exception.InitializationException;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.remote.Address;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddress;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddressSerializer;
import org.gradle.internal.serialize.FlushableEncoder;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.gradle.internal.stream.EncodedStream;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Handles the bootstrap protocol between the client and the daemon. Performs an
 * initial handshake with a new daemon, exchanging startup information and receiving
 * just enough resulting data to open a proper messaging channel with the daemon.
 */
public final class DaemonStartupCommunication {

    private static final Logger LOGGER = Logging.getLogger(DaemonStartupCommunication.class);

    private static final String ABOUT_TO_CLOSE_STREAMS = "Daemon started. About to close the streams. Daemon details: ";
    private static final byte[] DAEMON_RESPONSE_MAGIC_BYTES = ABOUT_TO_CLOSE_STREAMS.getBytes(StandardCharsets.US_ASCII);

    private DaemonStartupCommunication() {
        // Private to prevent instantiation
    }

    /**
     * Write the given daemon server configuration to the given output stream as binary data.
     */
    public static void writeDaemonServerConfiguration(OutputStream os, DaemonServerConfiguration configuration) {
        FlushableEncoder encoder = new KryoBackedEncoder(new EncodedStream.EncodedOutput(os));

        try {
            encoder.writeString(configuration.getGradleUserHomeDir().getAbsolutePath());
            encoder.writeString(configuration.getBaseDir().getAbsolutePath());
            encoder.writeSmallInt(configuration.getIdleTimeout());
            encoder.writeSmallInt(configuration.getPeriodicCheckIntervalMs());
            encoder.writeBoolean(configuration.isSingleUse());
            encoder.writeBoolean(configuration.isInstrumentationAgentAllowed());
            encoder.writeSmallInt(configuration.getNativeServicesMode().ordinal());
            encoder.writeString(configuration.getUid());
            encoder.writeSmallInt(configuration.getPriority().ordinal());
            encoder.writeSmallInt(configuration.getJvmOptions().size());
            for (String daemonOpt : configuration.getJvmOptions()) {
                encoder.writeString(daemonOpt);
            }
            encoder.flush();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    /**
     * Read the binary-encoded daemon server configuration from the given input stream.
     */
    public static DaemonServerConfiguration readDaemonServerConfiguration(InputStream is) {
        KryoBackedDecoder decoder = new KryoBackedDecoder(new EncodedStream.EncodedInput(is));

        try {
            File gradleUserHomeDir = new File(decoder.readString());
            File daemonBaseDir = new File(decoder.readString());
            int idleTimeoutMs = decoder.readSmallInt();
            int periodicCheckIntervalMs = decoder.readSmallInt();
            boolean singleUse = decoder.readBoolean();
            boolean instrumentationAgentAllowed = decoder.readBoolean();
            NativeServices.NativeServicesMode nativeServicesMode = NativeServices.NativeServicesMode.values()[decoder.readSmallInt()];
            String daemonUid = decoder.readString();
            DaemonPriority priority = DaemonPriority.values()[decoder.readSmallInt()];
            int argCount = decoder.readSmallInt();
            List<String> startupJvmOpts = new ArrayList<>(argCount);
            for (int i = 0; i < argCount; i++) {
                startupJvmOpts.add(decoder.readString());
            }

            return new DefaultDaemonServerConfiguration(
                gradleUserHomeDir,
                daemonUid,
                daemonBaseDir,
                idleTimeoutMs,
                periodicCheckIntervalMs,
                singleUse,
                priority,
                startupJvmOpts,
                instrumentationAgentAllowed,
                nativeServicesMode
            );
        } catch (EOFException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    /**
     * Writes the given daemon startup info to the given target. A magic string is first written
     * as ASCII, then the startup info is encoded as binary data. Finally, a newline is written.
     */
    public static void writeDaemonStartupInfo(PrintStream target, DaemonStartupInfo info) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        FlushableEncoder encoder = new KryoBackedEncoder(new EncodedStream.EncodedOutput(byteArrayOutputStream));

        try {
            byteArrayOutputStream.write(DAEMON_RESPONSE_MAGIC_BYTES);
            encoder.writeNullableString(info.getPid() == null ? null : info.getPid().toString());
            encoder.writeString(info.getUid());
            MultiChoiceAddress multiChoiceAddress = (MultiChoiceAddress) info.getAddress();
            new MultiChoiceAddressSerializer().write(encoder, multiChoiceAddress);
            encoder.writeString(info.getDiagnostics().getDaemonLog().getPath());
            encoder.flush();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        target.println(new String(byteArrayOutputStream.toByteArray(), StandardCharsets.US_ASCII));
    }

    /**
     * Read from the given input stream until the daemon's magic string is found. Then,
     * parse the daemon startup info from the rest of the line and return it.
     */
    public static DaemonStartupInfo readStartupInfoFromDaemonOutput(InputStream is) {
        // Wait for the process' stdout to indicate that the process has been started successfully
        String greeting = null;
        ArrayList<String> lines = new ArrayList<>();
        try (Scanner scanner = new Scanner(is, StandardCharsets.US_ASCII.name())) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                LOGGER.debug("Daemon output: {}", line);
                lines.add(line);
                if (line.contains("Listening for transport dt_socket at address")) {
                    // Pass-through JVM debug output to console so the user can see it.
                    LOGGER.lifecycle(line);
                }
                if (line.startsWith(ABOUT_TO_CLOSE_STREAMS)) {
                    greeting = line;
                    break;
                }
            }
        }

        if (greeting == null) {
            throw new InitializationException(
                "Could not parse daemon handshake response.\n" +
                    "Please read the following process output to find out more:\n" +
                    "-----------------------\n" +
                    String.join("\n", lines)
            );
        }

        return readDaemonStartupInfo(greeting);
    }

    public static DaemonStartupInfo readDaemonStartupInfo(String message) {
        byte[] data = message.getBytes(StandardCharsets.US_ASCII);
        int start = DAEMON_RESPONSE_MAGIC_BYTES.length;
        ByteArrayInputStream is = new ByteArrayInputStream(data, start, data.length - start);
        KryoBackedDecoder decoder = new KryoBackedDecoder(new EncodedStream.EncodedInput(is));

        try {
            String pidString = decoder.readNullableString();
            Long pid = pidString == null ? null : Long.parseLong(pidString);
            String uid = decoder.readString();
            Address address = new MultiChoiceAddressSerializer().read(decoder);
            File daemonLog = new File(decoder.readString());
            return new DaemonStartupInfo(uid, address, new DaemonDiagnostics(daemonLog, pid));
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

}
