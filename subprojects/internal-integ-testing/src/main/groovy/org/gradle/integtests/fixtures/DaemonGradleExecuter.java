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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DaemonGradleExecuter extends ForkingGradleExecuter {
    private static final String DAEMON_REGISTRY_SYS_PROP = "org.gradle.integtest.daemon.registry";
    private final GradleDistribution distribution;

    public DaemonGradleExecuter(GradleDistribution distribution) {
        super(distribution.getGradleHomeDir());
        this.distribution = distribution;
    }

    @Override
    public Map<String, String> getAllEnvironmentVars() {
        Map<String, String> vars = new HashMap<String, String>();
        vars.put("GRADLE_DAEMON_OPTS", "-XX:MaxPermSize=256m");
        vars.putAll(super.getAllEnvironmentVars());
        return vars;
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
}
