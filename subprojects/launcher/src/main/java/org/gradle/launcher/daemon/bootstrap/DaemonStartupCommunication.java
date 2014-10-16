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

package org.gradle.launcher.daemon.bootstrap;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.diagnostics.DaemonStartupInfo;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.internal.inet.MultiChoiceAddress;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.FlushableEncoder;
import org.gradle.messaging.serialize.InputStreamBackedDecoder;
import org.gradle.messaging.serialize.OutputStreamBackedEncoder;
import org.gradle.process.internal.child.EncodedStream;

import java.io.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DaemonStartupCommunication {

    private static final Logger LOGGER = Logging.getLogger(DaemonStartupCommunication.class);

    public void printDaemonStarted(PrintStream target, Long pid, String uid, Address address, File daemonLog) {
        target.print(daemonGreeting());

        // Encode as ascii
        try {
            OutputStream outputStream = new EncodedStream.EncodedOutput(target);
            FlushableEncoder encoder = new OutputStreamBackedEncoder(outputStream);
            encoder.writeNullableString(pid == null ? null : pid.toString());
            encoder.writeString(uid);
            MultiChoiceAddress multiChoiceAddress = (MultiChoiceAddress) address;
            UUID canonicalAddress = (UUID) multiChoiceAddress.getCanonicalAddress();
            encoder.writeLong(canonicalAddress.getMostSignificantBits());
            encoder.writeLong(canonicalAddress.getLeastSignificantBits());
            encoder.writeInt(multiChoiceAddress.getPort());
            encoder.writeSmallInt(multiChoiceAddress.getCandidates().size());
            for (InetAddress inetAddress : multiChoiceAddress.getCandidates()) {
                encoder.writeBinary(inetAddress.getAddress());
            }
            encoder.writeString(daemonLog.getPath());
            encoder.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        target.println();

        //ibm vm 1.6 + windows XP gotchas:
        //we need to print something else to the stream after we print the daemon greeting.
        //without it, the parent hangs without receiving the message above (flushing does not help).
        LOGGER.debug("Completed writing the daemon greeting. Closing streams...");
        //btw. the ibm vm+winXP also has some issues detecting closed streams by the child but we handle this problem differently.
    }

    public DaemonStartupInfo readDiagnostics(String message) {
        //Assuming the message has correct format. Not bullet proof, but seems to work ok for now.
        if (!message.startsWith(daemonGreeting())) {
            throw new IllegalArgumentException(String.format("Unexpected daemon startup message: %s", message));
        }
        try {
            String encoded = message.substring(daemonGreeting().length()).trim();
            InputStream inputStream = new EncodedStream.EncodedInput(new ByteArrayInputStream(encoded.getBytes()));
            Decoder decoder = new InputStreamBackedDecoder(inputStream);
            String pidString = decoder.readNullableString();
            String uid = decoder.readString();
            Long pid = pidString == null ? null : Long.valueOf(pidString);
            UUID canonicalAddress = new UUID(decoder.readLong(), decoder.readLong());
            int port = decoder.readInt();
            int addressCount = decoder.readSmallInt();
            List<InetAddress> addresses = new ArrayList<InetAddress>(addressCount);
            for (int i = 0; i < addressCount; i++) {
                InetAddress address = InetAddress.getByAddress(decoder.readBinary());
                addresses.add(address);
            }
            Address address = new MultiChoiceAddress(canonicalAddress, port, addresses);
            File daemonLog = new File(decoder.readString());
            return new DaemonStartupInfo(uid, address, new DaemonDiagnostics(daemonLog, pid));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean containsGreeting(String message) {
        if (message == null) {
            throw new IllegalArgumentException("Unable to detect the daemon greeting because the input message is null!");
        }
        return message.contains(daemonGreeting());
    }

    private static String daemonGreeting() {
        return DaemonMessages.ABOUT_TO_CLOSE_STREAMS;
    }
}
