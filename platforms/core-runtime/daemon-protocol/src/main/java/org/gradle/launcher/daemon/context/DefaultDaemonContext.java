/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.context;

import com.google.common.base.Joiner;
import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.launcher.daemon.configuration.DaemonPriority;
import org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Keep in mind that this is a serialized value object.
 */
public class DefaultDaemonContext implements DaemonContext {

    public static final org.gradle.internal.serialize.Serializer<DefaultDaemonContext> SERIALIZER = new Serializer();

    private final String uid;
    private final File javaHome;
    private final File daemonRegistryDir;
    private final Long pid;
    private final Integer idleTimeout;
    private final Collection<String> daemonOpts;
    private final boolean applyInstrumentationAgent;
    private final DaemonPriority priority;
    private final NativeServicesMode nativeServicesMode;
    private final JavaLanguageVersion javaVersion;
    private final String javaVendor;

    public DefaultDaemonContext(
        String uid,
        File javaHome,
        JavaLanguageVersion javaVersion,
        String javaVendor,
        File daemonRegistryDir,
        Long pid,
        Integer idleTimeout,
        Collection<String> daemonOpts,
        boolean applyInstrumentationAgent,
        NativeServicesMode nativeServicesMode,
        DaemonPriority priority
    ) {
        this.uid = uid;
        this.javaHome = javaHome;
        this.javaVersion = javaVersion;
        this.daemonRegistryDir = daemonRegistryDir;
        this.pid = pid;
        this.idleTimeout = idleTimeout;
        this.daemonOpts = daemonOpts;
        this.applyInstrumentationAgent = applyInstrumentationAgent;
        this.priority = priority;
        this.nativeServicesMode = nativeServicesMode;
        this.javaVendor = javaVendor;
    }

    @Override
    public String toString() {
        // Changes to this also affect org.gradle.integtests.fixtures.daemon.DaemonContextParser
        return String.format("DefaultDaemonContext[uid=%s,javaHome=%s,javaVersion=%s,javaVendor=%s,daemonRegistryDir=%s,pid=%s,idleTimeout=%s,priority=%s,applyInstrumentationAgent=%s,nativeServicesMode=%s,daemonOpts=%s]",
            uid, javaHome, javaVersion, javaVendor, daemonRegistryDir, pid, idleTimeout, priority, applyInstrumentationAgent, nativeServicesMode, Joiner.on(',').join(daemonOpts));
    }

    @Override
    public String getUid() {
        return uid;
    }

    @Override
    public File getJavaHome() {
        return javaHome;
    }

    @Override
    public JavaLanguageVersion getJavaVersion() {
        return javaVersion;
    }

    @Override
    public String getJavaVendor() {
        return javaVendor;
    }

    @Override
    public File getDaemonRegistryDir() {
        return daemonRegistryDir;
    }

    @Override
    public Long getPid() {
        return pid;
    }

    @Override
    public Integer getIdleTimeout() {
        return idleTimeout;
    }

    @Override
    public Collection<String> getDaemonOpts() {
        return daemonOpts;
    }

    @Override
    public boolean shouldApplyInstrumentationAgent() {
        return applyInstrumentationAgent;
    }

    @Override
    public NativeServicesMode getNativeServicesMode() {
        return nativeServicesMode;
    }

    @Override
    public DaemonPriority getPriority() {
        return priority;
    }

    @Override
    public DaemonRequestContext toRequest() {
        return new DaemonRequestContext(new DaemonJvmCriteria.JavaHome(DaemonJvmCriteria.JavaHome.Source.EXISTING_DAEMON, javaHome), this.getDaemonOpts(), this.shouldApplyInstrumentationAgent(), this.getNativeServicesMode(), this.getPriority());
    }

    @Override
    @SuppressWarnings("UndefinedEquals")
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultDaemonContext that = (DefaultDaemonContext) o;
        return applyInstrumentationAgent == that.applyInstrumentationAgent && Objects.equals(uid, that.uid) && Objects.equals(javaHome, that.javaHome) && Objects.equals(daemonRegistryDir, that.daemonRegistryDir) && Objects.equals(pid, that.pid) && Objects.equals(idleTimeout, that.idleTimeout) && Objects.equals(daemonOpts, that.daemonOpts) && priority == that.priority && nativeServicesMode == that.nativeServicesMode && Objects.equals(javaVersion, that.javaVersion) && Objects.equals(javaVendor, that.javaVendor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid, javaHome, daemonRegistryDir, pid, idleTimeout, daemonOpts, applyInstrumentationAgent, priority, nativeServicesMode, javaVersion, javaVendor);
    }

    static class Serializer implements org.gradle.internal.serialize.Serializer<DefaultDaemonContext> {

        @Override
        public DefaultDaemonContext read(Decoder decoder) throws Exception {
            String uid = decoder.readNullableString();
            String pathname = decoder.readString();
            File javaHome = new File(pathname);
            JavaLanguageVersion javaVersion = JavaLanguageVersion.of(decoder.readSmallInt());
            String javaVendor = decoder.readString();
            File registryDir = new File(decoder.readString());
            Long pid = decoder.readBoolean() ? decoder.readLong() : null;
            Integer idle = decoder.readBoolean() ? decoder.readInt() : null;
            int daemonOptCount = decoder.readInt();
            List<String> daemonOpts = new ArrayList<>(daemonOptCount);
            for (int i = 0; i < daemonOptCount; i++) {
                daemonOpts.add(decoder.readString());
            }
            boolean applyInstrumentationAgent = decoder.readBoolean();
            NativeServicesMode nativeServicesMode = NativeServicesMode.values()[decoder.readSmallInt()];
            DaemonPriority priority = decoder.readBoolean() ? DaemonPriority.values()[decoder.readInt()] : null;

            return new DefaultDaemonContext(uid, javaHome, javaVersion, javaVendor, registryDir, pid, idle, daemonOpts, applyInstrumentationAgent, nativeServicesMode, priority);
        }

        @Override
        public void write(Encoder encoder, DefaultDaemonContext context) throws Exception {
            encoder.writeNullableString(context.uid);
            encoder.writeString(context.javaHome.getPath());
            encoder.writeSmallInt(context.javaVersion.asInt());
            encoder.writeString(context.javaVendor);
            encoder.writeString(context.daemonRegistryDir.getPath());
            encoder.writeBoolean(context.pid != null);
            if (context.pid != null) {
                encoder.writeLong(context.pid);
            }
            encoder.writeBoolean(context.idleTimeout != null);
            if (context.idleTimeout != null) {
                encoder.writeInt(context.idleTimeout);
            }
            encoder.writeInt(context.daemonOpts.size());
            for (String daemonOpt : context.daemonOpts) {
                encoder.writeString(daemonOpt);
            }
            encoder.writeBoolean(context.applyInstrumentationAgent);
            encoder.writeSmallInt(context.nativeServicesMode.ordinal());
            encoder.writeBoolean(context.priority != null);
            if (context.priority != null) {
                encoder.writeInt(context.priority.ordinal());
            }
        }
    }
}
