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

import org.apache.commons.collections.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

public class 
        DaemonGradleExecuter extends ForkingGradleExecuter {
    private static final String DAEMON_REGISTRY_SYS_PROP = "org.gradle.integtest.daemon.registry";
    private final GradleDistribution distribution;
    private final File daemonBaseDir;
    private final boolean allowExtraLogging;

    public DaemonGradleExecuter(GradleDistribution distribution, File daemonBaseDir, boolean allowExtraLogging) {
        super(distribution.getGradleHomeDir());
        this.distribution = distribution;
        this.daemonBaseDir = daemonBaseDir;
        this.allowExtraLogging = allowExtraLogging;
    }

    @Override
    protected List<String> getAllArgs() {
        List<String> originalArgs = super.getAllArgs();

        List<String> args = new ArrayList<String>();
        args.add("--daemon");

        args.addAll(originalArgs);

        String daemonRegistryBase = getDaemonRegistryBase();
        if (daemonRegistryBase != null) {
            args.add("-Dorg.gradle.daemon.registry.base=" + daemonRegistryBase);
            configureJvmArgs(args, daemonRegistryBase);
        } else {
            configureJvmArgs(args, distribution.getUserHomeDir().getAbsolutePath());
        }

        if (!args.toString().contains("-Dorg.gradle.daemon.idletimeout=")) {
            //isolated daemons/daemon with custom base dir cannot be connected again
            //so they should have shorter timeout
            boolean preferShortTimeout = distribution.isUsingIsolatedDaemons() || daemonBaseDir != null;
            int timeout = preferShortTimeout? 20000 : 5 * 60 * 1000;
            args.add("-Dorg.gradle.daemon.idletimeout=" + timeout);
        }

        configureDefaultLogging(args);

        return args;
    }

    private void configureDefaultLogging(List<String> args) {
        if(!allowExtraLogging) {
            return;
        }
        List logOptions = asList("-i", "--info", "-d", "--debug", "-q", "--quite");
        boolean alreadyConfigured = CollectionUtils.containsAny(args, logOptions);
        if (!alreadyConfigured) {
            args.add("-i");
        }
    }

    private void configureJvmArgs(List<String> args, String registryBase) {
        // TODO - clean this up. It's a workaround to provide some way for the client of this executer to
        // specify that no jvm args should be provided
        if(!args.remove("-Dorg.gradle.jvmargs=")){
            args.add(0, "-Dorg.gradle.jvmargs=-ea -XX:MaxPermSize=256m"
                    + " -XX:+HeapDumpOnOutOfMemoryError");
        }
    }

    String getDaemonRegistryBase() {
        if (daemonBaseDir != null) {
            return daemonBaseDir.getAbsolutePath();
        }

        String customDaemonRegistryDir = System.getProperty(DAEMON_REGISTRY_SYS_PROP);
        if (customDaemonRegistryDir != null && !distribution.isUsingIsolatedDaemons()) {
            return customDaemonRegistryDir;
        }

        return null;
    }
}
