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
        List<String> originalArgs = super.getAllArgs();

        List<String> args = new ArrayList<String>();
        args.add("--daemon");
        args.add("-Dorg.gradle.daemon.idletimeout=" + (5 * 60 * 1000));
        String customDaemonRegistryDir = System.getProperty(DAEMON_REGISTRY_SYS_PROP);
        if (customDaemonRegistryDir != null && !distribution.isUsingIsolatedDaemons()) {
            args.add("-Dorg.gradle.daemon.registry.base=" + customDaemonRegistryDir);
        }
        
        args.addAll(originalArgs);

        // TODO - clean this up. It's a workaround to provide some way for the client of this executer to
        // specify that no jvm args should be provided
        if(!args.remove("-Dorg.gradle.jvmargs=")){
            args.add(0, "-Dorg.gradle.jvmargs=-ea -XX:MaxPermSize=256m");
        }
        
        return args;
    }
}
