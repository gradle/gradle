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

import org.gradle.internal.serialize.Decoder;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.Nullable;

/**
 * The layouts the daemon context has had inside {@code registry.bin}, and how to read each one.
 *
 * <p>The registry file carries no header naming its format, so a reader cannot discover the layout from
 * the bytes. It does not have to: the file sits in a directory named after the Gradle version that wrote
 * it, so the version is known before the file is opened and the layout follows from it.
 *
 * <p>These layouts describe released Gradle versions, which are immutable. A decoder for one of them is
 * correct permanently, because the version it describes will never be republished.
 *
 * <p>Each generation only ever added fields:
 *
 * <ul>
 *   <li>{@link #V1} up to 8.0</li>
 *   <li>{@link #V2} from 8.1, which added whether the instrumentation agent is applied</li>
 *   <li>{@link #V3} from 8.8, which added the Java version and the native services mode</li>
 *   <li>{@link #V4} from 8.10, which added the Java vendor</li>
 * </ul>
 */
public enum DaemonContextFormat {

    V1(false, false, false),
    V2(false, false, true),
    V3(true, false, true),
    V4(true, true, true);

    private static final GradleVersion V2_FROM = GradleVersion.version("8.1");
    private static final GradleVersion V3_FROM = GradleVersion.version("8.8");
    private static final GradleVersion V4_FROM = GradleVersion.version("8.10");

    private final boolean hasJavaVersion;
    private final boolean hasJavaVendor;
    private final boolean hasInstrumentationAgent;

    DaemonContextFormat(boolean hasJavaVersion, boolean hasJavaVendor, boolean hasInstrumentationAgent) {
        this.hasJavaVersion = hasJavaVersion;
        this.hasJavaVendor = hasJavaVendor;
        this.hasInstrumentationAgent = hasInstrumentationAgent;
    }

    /**
     * The layout written by the given Gradle version.
     */
    public static DaemonContextFormat forVersion(GradleVersion version) {
        GradleVersion baseVersion = version.getBaseVersion();
        if (baseVersion.compareTo(V4_FROM) >= 0) {
            return V4;
        }
        if (baseVersion.compareTo(V3_FROM) >= 0) {
            return V3;
        }
        if (baseVersion.compareTo(V2_FROM) >= 0) {
            return V2;
        }
        return V1;
    }

    /**
     * Reads one daemon context, consuming exactly the bytes it occupies so that the next record in the
     * file starts where this leaves off.
     */
    public Context read(Decoder decoder) throws Exception {
        String uid = decoder.readNullableString();
        String javaHome = decoder.readString();
        if (hasJavaVersion) {
            decoder.readSmallInt();
        }
        if (hasJavaVendor) {
            decoder.readString();
        }
        decoder.readString(); // daemon registry dir
        Long pid = decoder.readBoolean() ? decoder.readLong() : null;
        if (decoder.readBoolean()) {
            decoder.readInt(); // idle timeout
        }
        int daemonOptCount = decoder.readInt();
        for (int i = 0; i < daemonOptCount; i++) {
            decoder.readString();
        }
        if (hasInstrumentationAgent) {
            decoder.readBoolean();
        }
        if (hasJavaVersion) {
            decoder.readSmallInt(); // native services mode, added alongside the Java version
        }
        if (decoder.readBoolean()) {
            decoder.readInt(); // priority
        }
        return new Context(uid, pid, javaHome);
    }

    /**
     * The parts of a daemon context that managing a daemon actually needs.
     */
    public static final class Context {
        private final @Nullable String uid;
        private final @Nullable Long pid;
        private final String javaHome;

        Context(@Nullable String uid, @Nullable Long pid, String javaHome) {
            this.uid = uid;
            this.pid = pid;
            this.javaHome = javaHome;
        }

        public @Nullable String getUid() {
            return uid;
        }

        public @Nullable Long getPid() {
            return pid;
        }

        public String getJavaHome() {
            return javaHome;
        }
    }
}
