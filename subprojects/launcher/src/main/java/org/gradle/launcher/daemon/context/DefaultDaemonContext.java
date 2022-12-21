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
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.launcher.daemon.configuration.DaemonParameters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Keep in mind that this is a serialized value object.
 *
 * @see DaemonContextBuilder
 */
public class DefaultDaemonContext implements DaemonContext {

    public static final org.gradle.internal.serialize.Serializer<DefaultDaemonContext> SERIALIZER = new Serializer();

    private final String uid;
    private final File javaHome;
    private final File daemonRegistryDir;
    private final Long pid;
    private final Integer idleTimeout;
    private final List<String> daemonOpts;
    private final boolean applyInstrumentationAgent;
    private final DaemonParameters.Priority priority;

    public DefaultDaemonContext(
        String uid,
        File javaHome,
        File daemonRegistryDir,
        Long pid,
        Integer idleTimeout,
        List<String> daemonOpts,
        boolean applyInstrumentationAgent,
        DaemonParameters.Priority priority
    ) {
        this.uid = uid;
        this.javaHome = javaHome;
        this.daemonRegistryDir = daemonRegistryDir;
        this.pid = pid;
        this.idleTimeout = idleTimeout;
        this.daemonOpts = daemonOpts;
        this.applyInstrumentationAgent = applyInstrumentationAgent;
        this.priority = priority;
    }

    public String toString() {
        return String.format("DefaultDaemonContext[uid=%s,javaHome=%s,daemonRegistryDir=%s,pid=%s,idleTimeout=%s,priority=%s,applyInstrumentationAgent=%s,daemonOpts=%s]",
            uid, javaHome, daemonRegistryDir, pid, idleTimeout, priority, applyInstrumentationAgent, Joiner.on(',').join(daemonOpts));
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
    public List<String> getDaemonOpts() {
        return daemonOpts;
    }

    @Override
    public boolean shouldApplyInstrumentationAgent() {
        return applyInstrumentationAgent;
    }

    @Override
    public DaemonParameters.Priority getPriority() {
        return priority;
    }

    private static class Serializer implements org.gradle.internal.serialize.Serializer<DefaultDaemonContext> {

        @Override
        public DefaultDaemonContext read(Decoder decoder) throws Exception {
            String uid = decoder.readNullableString();
            String pathname = decoder.readString();
            File javaHome = new File(pathname);
            File registryDir = new File(decoder.readString());
            Long pid = decoder.readBoolean() ? decoder.readLong() : null;
            Integer idle = decoder.readBoolean() ? decoder.readInt() : null;
            int daemonOptCount = decoder.readInt();
            List<String> daemonOpts = new ArrayList<String>(daemonOptCount);
            for (int i = 0; i < daemonOptCount; i++) {
                daemonOpts.add(decoder.readString());
            }
            boolean applyInstrumentationAgent = decoder.readBoolean();
            DaemonParameters.Priority priority = decoder.readBoolean() ? DaemonParameters.Priority.values()[decoder.readInt()] : null;
            return new DefaultDaemonContext(uid, javaHome, registryDir, pid, idle, daemonOpts, applyInstrumentationAgent, priority);
        }

        @Override
        public void write(Encoder encoder, DefaultDaemonContext context) throws Exception {
            encoder.writeNullableString(context.uid);
            encoder.writeString(context.javaHome.getPath());
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
            encoder.writeBoolean(context.priority != null);
            if (context.priority != null) {
                encoder.writeInt(context.priority.ordinal());
            }
        }
    }
}
