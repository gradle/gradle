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
import org.gradle.api.JavaVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

public class DaemonGradleExecuter extends ForkingGradleExecuter {

    private final GradleDistribution distribution;
    private final boolean allowExtraLogging;
    private final boolean noDefaultJvmArgs;

    public DaemonGradleExecuter(GradleDistribution distribution, boolean allowExtraLogging, boolean noDefaultJvmArgs) {
        super(distribution.getGradleHomeDir());
        this.distribution = distribution;
        this.allowExtraLogging = allowExtraLogging;
        this.noDefaultJvmArgs = noDefaultJvmArgs;
    }

    @Override
    protected int getDaemonIdleTimeoutSecs() {
        int superValue =  super.getDaemonIdleTimeoutSecs();
        boolean preferShortTimeout = distribution.isUsingIsolatedDaemons() || getDaemonBaseDir() != null;
        if (preferShortTimeout) {
            return Math.min(superValue, 20);
        } else {
            return superValue;
        }
    }

    @Override
    protected File getDaemonBaseDir() {
        File daemonBaseDir = super.getDaemonBaseDir();
        if (distribution.isUsingIsolatedDaemons() && daemonBaseDir == null) {
            return new File(distribution.getUserHomeDir(), "daemon");
        } else {
            return daemonBaseDir;
        }
    }

    @Override
    protected List<String> getAllArgs() {
        List<String> originalArgs = super.getAllArgs();

        List<String> args = new ArrayList<String>();
        args.add("--daemon");

        args.addAll(originalArgs);
        configureJvmArgs(args);
        configureDefaultLogging(args);

        return args;
    }

    private void configureDefaultLogging(List<String> args) {
        if(!allowExtraLogging) {
            return;
        }
        List logOptions = asList("-i", "--info", "-d", "--debug", "-q", "--quiet");
        boolean alreadyConfigured = CollectionUtils.containsAny(args, logOptions);
        if (!alreadyConfigured) {
            args.add("-i");
        }
    }

    private void configureJvmArgs(List<String> args) {
        if(!noDefaultJvmArgs) {
            String jvmArgs  = "-Dorg.gradle.jvmargs=-ea -XX:MaxPermSize=256m -XX:+HeapDumpOnOutOfMemoryError";
            if (JavaVersion.current().isJava5()) {
                jvmArgs = String.format("%s %s", jvmArgs, "-XX:+CMSPermGenSweepingEnabled");
            }
            args.add(0, jvmArgs);
        }
    }

}
