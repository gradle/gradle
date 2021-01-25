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

import org.gradle.api.specs.Spec
import org.gradle.integtests.fixtures.compatibility.MultiVersionTest
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.internal.jvm.JavaInfo
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.util.VersionNumber
import org.junit.Assume
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

import static org.gradle.integtests.fixtures.AvailableJavaHomes.getAvailableJdk

@MultiVersionTest
class DaemonMultiJdkIntegrationTest extends DaemonIntegrationSpec {
    static def version
    @Rule
    IgnoreIfJdkNotFound ignoreRule = new IgnoreIfJdkNotFound()

    JavaInfo jdk

    static VersionNumber getVersionNumber() {
        VersionNumber.parse(version.toString())
    }

    class IgnoreIfJdkNotFound implements TestRule {
        @Override
        Statement apply(Statement base, Description description) {
            jdk = getAvailableJdk(new Spec<JvmInstallationMetadata>() {
                @Override
                boolean isSatisfiedBy(JvmInstallationMetadata install) {
                    if (version.hasProperty("vendor")) {
                        def actualVendor = install.getVendor().getKnownVendor()
                        def expectedVendor = version.vendor
                        if (actualVendor != expectedVendor) {
                            return false
                        }
                    }
                    def actualVersion = install.languageVersion.majorVersion
                    def expectedVersion = version.version
                    return actualVersion == expectedVersion
                }
            })

            return {
                Assume.assumeTrue("$version.vendor JDK $version.version not found.", jdk != null)
                base.evaluate()
            }
        }
    }
}
