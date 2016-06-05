/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.daemon.fixtures

import org.gradle.api.JavaVersion
import org.gradle.api.specs.Spec
import org.gradle.integtests.fixtures.MultiVersionSpecRunner
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.integtests.fixtures.jvm.JvmInstallation
import org.gradle.internal.jvm.JavaInfo
import org.gradle.internal.jvm.Jvm
import org.gradle.util.EmptyStatement
import org.gradle.util.VersionNumber
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

import java.util.regex.Matcher
import java.util.regex.Pattern

import static org.gradle.integtests.fixtures.AvailableJavaHomes.getAvailableJdk

@RunWith(MultiVersionSpecRunner)
class DaemonMultiJdkIntegrationTest extends DaemonIntegrationSpec {
    static def version
    @Rule IgnoreIfJdkNotFound ignoreRule = new IgnoreIfJdkNotFound()

    JavaInfo jdk

    static VersionNumber getVersionNumber() {
        VersionNumber.parse(version.toString())
    }

    /**
     * This is a filthy hack to get something working quickly.  This would be nice to roll into
     * something like JvmVersionDetector and make it a 1st class bit of information about the
     * install.
     */
    public JdkVendor getJavaVendor(String javaCommand) {
        try {
            def out = new StringBuffer()
            def err = new StringBuffer()
            Process cmd = "${javaCommand} -XshowSettings:properties -version".execute()
            cmd.consumeProcessOutput(out, err)
            cmd.waitFor()
            return parseCommandOutput(err.toString())
        } catch (Exception e) {
            e.printStackTrace()
            return JdkVendor.UNKNOWN;
        }
    }

    JdkVendor parseCommandOutput(String output) {
        for (String line : output.readLines()) {
            Matcher matcher = Pattern.compile("\\s*(?:java.vm.name) = (.+?)").matcher(line);
            if (matcher.matches()) {
                return JdkVendor.from(matcher.group(1));
            }
        }
    }

    class IgnoreIfJdkNotFound implements TestRule {
        @Override
        Statement apply(Statement base, Description description) {
            jdk = getAvailableJdk(new Spec<JvmInstallation>() {
                @Override
                boolean isSatisfiedBy(JvmInstallation install) {
                    if (version.hasProperty("vendor")) {
                        JdkVendor vendor = getJavaVendor(Jvm.forHome(install.javaHome).javaExecutable.absolutePath)
                        if (vendor != version.vendor) {
                            return false
                        }
                    }
                    def installVersion = install.javaVersion
                    def thisVersion = JavaVersion.toVersion(version.version)
                    return install.javaVersion == JavaVersion.toVersion(version.version)
                }
            })

            if (jdk != null) {
                return base
            } else {
                return EmptyStatement.INSTANCE
            }
        }
    }
}
