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
import org.gradle.api.Transformer;
import org.gradle.test.fixtures.file.TestDirectoryProvider;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.apache.commons.collections.CollectionUtils.containsAny;
import static org.gradle.util.CollectionUtils.collect;
import static org.gradle.util.CollectionUtils.join;

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
    protected List<String> getGradleOpts() {
        if (isNoDefaultJvmArgs()) {
            return super.getGradleOpts();
        } else {
            // Workaround for http://issues.gradle.org/browse/GRADLE-2629
            // Instead of just adding these as standalone opts, we need to add to
            // -Dorg.gradle.jvmArgs in order for them to be effectual
            List<String> jvmArgs = new ArrayList<String>(4);
            jvmArgs.add("-XX:MaxPermSize=320m");
            jvmArgs.add("-XX:+HeapDumpOnOutOfMemoryError");
            jvmArgs.add("-XX:HeapDumpPath=" + buildContext.getGradleUserHomeDir().getAbsolutePath());
            if (JavaVersion.current().isJava5()) {
                jvmArgs.add("-XX:+CMSPermGenSweepingEnabled");
                jvmArgs.add("-Dcom.sun.management.jmxremote");
            }

            String quotedArgs = join(" ", collect(jvmArgs, new Transformer<String, String>() {
                public String transform(String input) {
                    return String.format("'%s'", input);
                }
            }));

            List<String> gradleOpts = new ArrayList<String>(super.getGradleOpts());
            gradleOpts.add("-Dorg.gradle.jvmArgs=" +  quotedArgs);
            return gradleOpts;
        }
    }

}
