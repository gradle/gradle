/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.launcher.daemon.configuration.DaemonPriority;
import org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria;

import java.util.Collection;

/**
 * Represents the request context a client has made.
 *
 * This contains the list of properties a client will consider when determining if a daemon is compatible.
 */
public class DaemonRequestContext {
    private final DaemonJvmCriteria jvmCriteria;
    private final Collection<String> daemonOpts;
    private final boolean applyInstrumentationAgent;
    private final NativeServices.NativeServicesMode nativeServicesMode;
    private final DaemonPriority priority;

    public DaemonRequestContext(DaemonJvmCriteria jvmCriteria, Collection<String> daemonOpts, boolean applyInstrumentationAgent, NativeServices.NativeServicesMode nativeServicesMode, DaemonPriority priority) {
        this.jvmCriteria = jvmCriteria;
        this.daemonOpts = daemonOpts;
        this.applyInstrumentationAgent = applyInstrumentationAgent;
        this.nativeServicesMode = nativeServicesMode;
        this.priority = priority;
    }

    public DaemonJvmCriteria getJvmCriteria() {
        return jvmCriteria;
    }

    public Collection<String> getDaemonOpts() {
        return daemonOpts;
    }

    public boolean shouldApplyInstrumentationAgent() {
        return applyInstrumentationAgent;
    }

    public NativeServices.NativeServicesMode getNativeServicesMode() {
        return nativeServicesMode;
    }

    public DaemonPriority getPriority() {
        return priority;
    }

    @Override
    public String toString() {
        return "DaemonRequestContext{" +
            "jvmCriteria=" + jvmCriteria +
            ", daemonOpts=" + daemonOpts +
            ", applyInstrumentationAgent=" + applyInstrumentationAgent +
            ", nativeServicesMode=" + nativeServicesMode +
            ", priority=" + priority +
            '}';
    }
}
