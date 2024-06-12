/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.tasks.diagnostics

import com.google.common.base.StandardSystemProperty
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.test.fixtures.file.LeaksFileHandles

import java.util.regex.Pattern

class BuildEnvironmentReportTaskIntegrationTest extends AbstractIntegrationSpec {
    def "reports Build JVM information"() {
        when:
        run(":buildEnvironment")

        then:
        // Not asserting over the exact output, just that important info is printed
        JvmVendor currentVendor = JvmVendor.fromString(StandardSystemProperty.JAVA_VM_VENDOR.value())
        output.matches(
            "(\n|.)*Build JVM: ${Pattern.quote(currentVendor.displayName)} .*${Pattern.quote(StandardSystemProperty.JAVA_VM_VERSION.value())}.*\\n" +
                ".*Location:\\s+${Pattern.quote(StandardSystemProperty.JAVA_HOME.value())}.*\\n" +
                ".*Language Version:\\s+${Jvm.current().javaVersionMajor}.*\\n" +
                ".*Vendor:\\s+${Pattern.quote(currentVendor.displayName)}.*\\n" +
                ".*Architecture:\\s+${Pattern.quote(StandardSystemProperty.OS_ARCH.value())}.*\\n" +
                ".*Is JDK:\\s+${Jvm.current().jdk}.*(\\n|.)*"
        )
    }

    @LeaksFileHandles("Putting an generated Jar on the classpath of the buildscript")
    def "reports external dependency name and version change"() {
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()
        mavenRepo.module("org", "leaf3").publish()
        mavenRepo.module("org", "leaf4").publish()

        mavenRepo.module("org", "toplevel1").dependsOnModules('leaf1', 'leaf2').publish()
        mavenRepo.module("org", "toplevel2").dependsOnModules('leaf3', 'leaf4').publish()

        createDirs("client", "impl")
        file("settings.gradle") << "include 'client', 'impl'"

        buildFile << """
            buildscript {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                dependencies {
                    classpath 'org:toplevel1:1.0'
                }
            }

            project(":impl") {
                buildscript {
                    repositories {
                        maven { url "${mavenRepo.uri}" }
                    }
                    dependencies {
                        classpath 'org:toplevel2:1.0'
                    }
                }

                configurations {
                    config1
                }
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                dependencies {
                    config1 'org:leaf1:1.0'
                }
            }
"""

        when:
        run(":impl:buildEnvironment")

        then:
        outputContains """
classpath
\\--- org:toplevel2:1.0
     +--- org:leaf3:1.0
     \\--- org:leaf4:1.0
"""
        when:
        run(":client:buildEnvironment")

        then:
        outputContains """
classpath
No dependencies
"""

        when:
        run(":buildEnvironment")

        then:
        outputContains """
classpath
\\--- org:toplevel1:1.0
     +--- org:leaf1:1.0
     \\--- org:leaf2:1.0
"""
    }
}
