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
import org.gradle.internal.remote.Address;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddress;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddressSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.FlushableEncoder;
import org.gradle.internal.serialize.InputStreamBackedDecoder;
import org.gradle.internal.serialize.OutputStreamBackedEncoder;
import org.gradle.internal.stream.EncodedStream;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.daemon.diagnostics.DaemonStartupInfo;
import org.gradle.launcher.daemon.logging.DaemonMessages;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

public class DaemonStartupCommunication {

    private static final Logger LOGGER = Logging.getLogger(DaemonStartupCommunication.class);

    public void printDaemonStarted(PrintStream target, Long pid, String uid, Address address, File daemonLog) {

        // Encode as ascii
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            byteArrayOutputStream.write(daemonGreeting().getBytes());
            OutputStream outputStream = new EncodedStream.EncodedOutput(byteArrayOutputStream);
            FlushableEncoder encoder = new OutputStreamBackedEncoder(outputStream);
            encoder.writeNullableString(pid == null ? null : pid.toString());
            encoder.writeString(uid);
            MultiChoiceAddress multiChoiceAddress = (MultiChoiceAddress) address;
            new MultiChoiceAddressSerializer().write(encoder, multiChoiceAddress);
            encoder.writeString(daemonLog.getPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        target.println(byteArrayOutputStream.toString());

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
            Address address = new MultiChoiceAddressSerializer().read(decoder);
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
