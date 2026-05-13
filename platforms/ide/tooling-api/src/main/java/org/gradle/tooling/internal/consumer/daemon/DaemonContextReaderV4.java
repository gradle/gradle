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
package org.gradle.tooling.internal.consumer.daemon;

import org.gradle.internal.serialize.Decoder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * DaemonContext reader for Gradle 8.10+. Adds {@code javaVendor} (string) after
 * {@code javaVersion}.
 *
 * <pre>
 *   uid                       (nullable string)
 *   javaHome                  (string)
 *   javaVersion               (smallInt)
 *   javaVendor                (string)
 *   registryDir               (string)
 *   pid                       (boolean + long?)
 *   idle                      (boolean + int?)
 *   daemonOpts                (int count + strings)
 *   applyInstrumentationAgent (boolean)
 *   nativeServicesMode        (smallInt)
 *   priority                  (boolean + int?)
 * </pre>
 *
 * <p>This layout MUST stay in sync with {@code DefaultDaemonContext.Serializer}. The
 * {@code CurrentFormatDriftTest} unit test guards against drift.
 */
final class DaemonContextReaderV4 implements DaemonContextReader {
    @Override
    public DaemonContextView read(Decoder decoder) throws Exception {
        String uid = decoder.readNullableString();
        File javaHome = new File(decoder.readString());
        int javaMajorVersion = decoder.readSmallInt();
        String javaVendor = decoder.readString();
        decoder.readString();                                       // registryDir
        Long pid = decoder.readBoolean() ? decoder.readLong() : null;
        Integer idleTimeoutMillis = decoder.readBoolean() ? decoder.readInt() : null;

        int daemonOptCount = decoder.readInt();
        List<String> daemonOpts = new ArrayList<>(daemonOptCount);
        for (int i = 0; i < daemonOptCount; i++) {
            daemonOpts.add(decoder.readString());
        }

        decoder.readBoolean();                                      // applyInstrumentationAgent
        decoder.readSmallInt();                                     // nativeServicesMode ordinal

        if (decoder.readBoolean()) {                                // priority present
            decoder.readInt();
        }

        return new DaemonContextView(uid, javaHome, javaMajorVersion, javaVendor, pid, idleTimeoutMillis, daemonOpts);
    }
}
