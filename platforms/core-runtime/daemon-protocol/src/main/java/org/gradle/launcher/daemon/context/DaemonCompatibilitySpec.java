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

import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;
import org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class DaemonCompatibilitySpec implements ExplainingSpec<DaemonContext> {

    private final DaemonRequestContext desiredContext;

    public DaemonCompatibilitySpec(DaemonRequestContext desiredContext) {
        this.desiredContext = desiredContext;
    }

    @Override
    public boolean isSatisfiedBy(DaemonContext potentialContext) {
        return whyUnsatisfied(potentialContext) == null;
    }

    @Override
    public String whyUnsatisfied(DaemonContext context) {
        if (!jvmCompatible(context)) {
            return "JVM is incompatible.\n" + description(context);
        } else if (!daemonOptsMatch(context)) {
            return "At least one daemon option is different.\n" + description(context);
        } else if (!priorityMatches(context)) {
            return "Process priority is different.\n" + description(context);
        } else if (!agentStatusMatches(context)) {
            return "Agent status is different.\n" + description(context);
        } else if (!nativeServicesModeMatches(context)) {
            return "Native services mode is different.\n" + description(context);
        }
        return null;
    }

    private String description(DaemonContext context) {
        return "Wanted: " + this + "\n"
            + "Actual: " + context + "\n";
    }

    private boolean daemonOptsMatch(DaemonContext potentialContext) {
        return potentialContext.getDaemonOpts().containsAll(desiredContext.getDaemonOpts())
            && potentialContext.getDaemonOpts().size() == desiredContext.getDaemonOpts().size();
    }

    private boolean jvmCompatible(DaemonContext potentialContext) {
        DaemonJvmCriteria criteria = desiredContext.getJvmCriteria();
        if (criteria instanceof DaemonJvmCriteria.Spec) {
            return ((DaemonJvmCriteria.Spec) criteria).isCompatibleWith(potentialContext.getJavaVersion(), potentialContext.getJavaVendor());
        }
        try {
            File potentialJavaHome = potentialContext.getJavaHome();
            JavaInfo desiredJavaInfo;
            if (criteria instanceof DaemonJvmCriteria.JavaHome) {
                desiredJavaInfo = Jvm.forHome(((DaemonJvmCriteria.JavaHome) criteria).getJavaHome());
            } else if (criteria instanceof DaemonJvmCriteria.LauncherJvm) {
                desiredJavaInfo = Jvm.current();
            } else {
                throw new IllegalStateException("Unknown DaemonJvmCriteria type: " + criteria.getClass().getName());
            }
            if (potentialJavaHome.exists() && desiredJavaInfo != null) {
                File potentialJava = Jvm.forHome(potentialJavaHome).getJavaExecutable();
                File desiredJava = desiredJavaInfo.getJavaExecutable();
                return Files.isSameFile(potentialJava.toPath(), desiredJava.toPath());
            }
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    private boolean priorityMatches(DaemonContext context) {
        return desiredContext.getPriority() == context.getPriority();
    }

    private boolean agentStatusMatches(DaemonContext context) {
        return desiredContext.shouldApplyInstrumentationAgent() == context.shouldApplyInstrumentationAgent();
    }

    private boolean nativeServicesModeMatches(DaemonContext context) {
        return desiredContext.getNativeServicesMode() == context.getNativeServicesMode();
    }

    @Override
    public String toString() {
        return desiredContext.toString();
    }
}
