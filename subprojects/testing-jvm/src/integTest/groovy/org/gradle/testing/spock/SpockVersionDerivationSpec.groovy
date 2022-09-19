/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.testing.spock

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.GroovyCoverage
import org.gradle.util.GradleVersion
import org.gradle.util.internal.GroovyDependencyUtil

@TargetCoverage({ GroovyCoverage.SUPPORTED_BY_JDK })
class SpockVersionDerivationSpec extends MultiVersionIntegrationSpec {
    def 'spock version adapts to Groovy version if main sourceset present'() {
        given:
        buildScript("""
            plugins {
                id 'groovy'
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation '${GroovyDependencyUtil.groovyModuleDependency('groovy', versionNumber)}'
            }

            testing {
                suites {
                    integTest(JvmTestSuite) {
                        useSpock()
                    }
                }
            }

            task checkConfiguration {
                dependsOn integTest
                doLast {
                    def expectedSpockVersion = testing.suites.integTest.getDerivedSpockVersion().get()
                    def expectedSpockCoreJar = "spock-core-\${expectedSpockVersion}.jar"

                    assert configurations.integTestRuntimeClasspath.files.any { it.name == "\$expectedSpockCoreJar" }
                }
            }
        """)

        expect:
        executer.expectDeprecationWarning("Resolution of the configuration :detachedConfiguration42 was attempted from a context different than the project context. Have a look at the documentation to understand why this is a problem and how it can be resolved. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 8.0. See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details.")
        succeeds("checkConfiguration")
    }

    def 'spock version adapts to Groovy version if main sourceset absent'() {
        given:
        buildScript("""
            plugins {
                id 'jvm-test-suite'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    integTest(JvmTestSuite) {
                        useSpock()

                        dependencies {
                            implementation '${GroovyDependencyUtil.groovyModuleDependency('groovy', versionNumber)}'
                        }
                    }
                }
            }

            task checkConfiguration {
                dependsOn integTest
                doLast {
                    def expectedSpockVersion = testing.suites.integTest.getDerivedSpockVersion().get()
                    def expectedSpockCoreJar = "spock-core-\${expectedSpockVersion}.jar"

                    assert configurations.integTestRuntimeClasspath.files.any { it.name == "\$expectedSpockCoreJar" }
                }
            }
        """)

        expect:
        executer.expectDeprecationWarning("Resolution of the configuration :detachedConfiguration42 was attempted from a context different than the project context. Have a look at the documentation to understand why this is a problem and how it can be resolved. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 8.0. See https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sub:resolving-unsafe-configuration-resolution-errors for more details.")
        succeeds("checkConfiguration")
    }
}
