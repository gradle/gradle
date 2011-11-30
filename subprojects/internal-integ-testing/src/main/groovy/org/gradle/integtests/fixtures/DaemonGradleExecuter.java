/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests.fixtures;

import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.server.DaemonIdleTimeout;

import java.util.ArrayList;
import java.util.List;

public class DaemonGradleExecuter extends ForkingGradleExecuter {
    private static final String DAEMON_REGISTRY_SYS_PROP = "org.gradle.integtest.daemon.registry";
    private final GradleDistribution distribution;

    public DaemonGradleExecuter(GradleDistribution distribution) {
        super(distribution.getGradleHomeDir());
        this.distribution = distribution;
    }

    @Override
    protected List<String> getAllArgs() {
        List<String> args = new ArrayList<String>();
        args.add("--daemon");
        args.add(DaemonIdleTimeout.toCliArg(5 * 60 * 1000));
        String customDaemonRegistryDir = System.getProperty(DAEMON_REGISTRY_SYS_PROP);
        if (customDaemonRegistryDir != null && !distribution.isUsingOwnUserHomeDir()) {
            args.add(DaemonDir.toCliArg(customDaemonRegistryDir));
        }
        args.addAll(super.getAllArgs());
        return args;
    }

    public GradleHandle createHandle() {
        return new DaemonGradleHandle(this);
    }

    protected static class DaemonGradleHandle extends ForkingGradleHandle {
        public DaemonGradleHandle(ForkingGradleExecuter executer) {
            super(executer);
        }

        protected String transformStandardOutput(String output) {
            output = output.replace(String.format("Note: the Gradle build daemon is an experimental feature.%n"), "");
            output = output.replace(String.format("As such, you may experience unexpected build failures. You may need to occasionally stop the daemon.%n"), "");
            return output;
        }
    }

}
