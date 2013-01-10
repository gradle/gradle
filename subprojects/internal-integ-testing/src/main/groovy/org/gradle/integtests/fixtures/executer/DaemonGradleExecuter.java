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
package org.gradle.integtests.fixtures.executer;

import org.gradle.api.JavaVersion;
import org.gradle.test.fixtures.file.TestDirectoryProvider;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.apache.commons.collections.CollectionUtils.containsAny;

public class DaemonGradleExecuter extends ForkingGradleExecuter {

    public DaemonGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        super(distribution, testDirectoryProvider);
    }

    @Override
    protected List<String> getAllArgs() {
        List<String> args = new ArrayList<String>(super.getAllArgs());
        args.add(0, "--daemon");

        if(!isQuiet() && isAllowExtraLogging()) {
            if (!containsAny(args, asList("-i", "--info", "-d", "--debug", "-q", "--quiet"))) {
                args.add(0, "-i");
            }
        }

        // Workaround for http://issues.gradle.org/browse/GRADLE-2625
        if (getUserHomeDir() != null) {
            args.add(String.format("-Duser.home=%s", getUserHomeDir().getPath()));
        }

        return args;
    }

    @Override
    public List<String> getGradleOpts() {
        if (isNoDefaultJvmArgs()) {
            return super.getGradleOpts();
        } else {
            List<String> gradleOpts = new ArrayList<String>(super.getGradleOpts());
            gradleOpts.add(0, "-XX:MaxPermSize=256m");
            gradleOpts.add(0, "-XX:+HeapDumpOnOutOfMemoryError");
            if (JavaVersion.current().isJava5()) {
                gradleOpts.add("-XX:+CMSPermGenSweepingEnabled");
                gradleOpts.add("-Dcom.sun.management.jmxremote");
            }
            return gradleOpts;
        }
    }

}
