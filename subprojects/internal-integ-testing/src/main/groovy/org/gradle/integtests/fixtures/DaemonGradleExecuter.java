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
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

public class DaemonGradleExecuter extends ForkingGradleExecuter {

    private final GradleDistribution distribution;
    private final boolean allowExtraLogging;

    public DaemonGradleExecuter(GradleDistribution distribution, boolean allowExtraLogging) {
        super(distribution.getGradleHomeDir());
        this.distribution = distribution;
        this.allowExtraLogging = allowExtraLogging;
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
        // TODO - clean this up. It's a workaround to provide some way for the client of this executer to
        // specify that no jvm args should be provided
        if(!args.remove("-Dorg.gradle.jvmargs=")){
            args.add(0, "-Dorg.gradle.jvmargs=-ea -XX:MaxPermSize=256m"
                    + " -XX:+HeapDumpOnOutOfMemoryError");
        }

        if (JavaVersion.current().isJava5()) {
            final String base = "-Dorg.gradle.jvmargs=";
            final String permGenSweepingOption = "-XX:+CMSPermGenSweepingEnabled";

            Spec<String> jvmArgsSpec = new Spec<String>() {
                public boolean isSatisfiedBy(String element) {
                    return element.startsWith(base);
                }
            };

            Transformer<String, String> addPermGenSweeping = new Transformer<String, String>() {
                public String transform(String original) {
                    return String.format("%s %s", original, permGenSweepingOption);
                }
            };

            boolean replaced = org.gradle.util.CollectionUtils.replace(args, jvmArgsSpec, addPermGenSweeping);
            if (!replaced) {
                args.add(String.format("%s%s", base, permGenSweepingOption));
            }
        }
    }

}
