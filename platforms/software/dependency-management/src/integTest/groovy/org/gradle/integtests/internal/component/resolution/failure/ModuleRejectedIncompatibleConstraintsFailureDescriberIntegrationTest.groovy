/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.internal.component.resolution.failure

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.component.resolution.failure.describer.ModuleRejectedIncompatibleConstraintsFailureDescriber
import org.gradle.util.GradleVersion

/**
 * This test verifies that the {@link ModuleRejectedIncompatibleConstraintsFailureDescriber} correctly describes
 * the failure when a module is rejected due to multiple conflicting version constraints.
 */
class ModuleRejectedIncompatibleConstraintsFailureDescriberIntegrationTest extends AbstractIntegrationSpec {

    def "constraints with same version and different direct origins are properly summarized"() {
        given:
        buildFile("""
            dependencies {
                api("org.apache.httpcomponents:httpclient")
            }
        """)

        groovyFile("a/build.gradle", """
            dependencies {
                constraints {
                    api("org.apache.httpcomponents:httpclient") {
                        version {
                            strictly("4.1.0")
                        }
                    }
                }
            }
        """)

        groovyFile("b/build.gradle", """
            dependencies {
                constraints {
                    api("org.apache.httpcomponents:httpclient") {
                        version {
                            strictly("4.2.0")
                        }
                    }
                }
            }
        """)

        groovyFile("c/build.gradle", """
            dependencies {
                constraints {
                    api("org.apache.httpcomponents:httpclient") {
                        version {
                            strictly("4.2.0")
                        }
                    }
                }
            }
        """)

        when:
        fails("forceResolution")

        then: "Has error output"
        failure.assertHasCause("Could not resolve all files for configuration ':compileClasspath'.")
        failure.assertHasCause("Could not resolve org.apache.httpcomponents:httpclient.")
        failure.assertHasCause("""Component is the target of multiple version constraints with conflicting requirements:
4.1.0 - directly in 'project :a' (apiElements)
4.2.0 - directly in 'project :b' (apiElements) (1 other path to this version)""")

        and: "Helpful resolutions are provided"
        failure.assertHasResolution("Run with :dependencyInsight --configuration compileClasspath --dependency org.apache.httpcomponents:httpclient to get more insight on how to solve the conflict.")
        failure.assertHasResolution("Debugging using the dependencyInsight report is described in more detail at: https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sec:identifying-reason-dependency-selection.")
    }

    def "constraints with same version and different transitive paths are summarized"() {
        given:
        buildFile("""
            dependencies {
                api("org.apache.httpcomponents:httpclient")
            }
        """)

        groovyFile("a/build.gradle", """
            dependencies {
                constraints {
                    api("org.apache.httpcomponents:httpclient") {
                        version {
                            strictly("4.1.0")
                        }
                    }
                }
            }
        """)

        groovyFile("b/b1/build.gradle", """
            dependencies {
                constraints {
                    api("org.apache.httpcomponents:httpclient") {
                        version {
                            strictly("4.2.0")
                        }
                    }
                }
            }
        """)

        groovyFile("b/b2/build.gradle", """
            dependencies {
                constraints {
                    api("org.apache.httpcomponents:httpclient") {
                        version {
                            strictly("4.2.0")
                        }
                    }
                }
            }
        """)

        groovyFile("c/c1/build.gradle", """
            dependencies {
                constraints {
                    api("org.apache.httpcomponents:httpclient") {
                        version {
                            strictly("4.2.0")
                        }
                    }
                }
            }
        """)

        when:
        fails("forceResolution")

        then: "Has error output"
        failure.assertHasCause("Could not resolve all files for configuration ':compileClasspath'.")
        failure.assertHasCause("Could not resolve org.apache.httpcomponents:httpclient.")
        failure.assertHasCause("""Component is the target of multiple version constraints with conflicting requirements:
4.1.0 - directly in 'project :a' (apiElements)
4.2.0 - transitively via 'project :b' (apiElements) (1 other path to this version)""")

        and: "Helpful resolutions are provided"
        failure.assertHasResolution("Run with :dependencyInsight --configuration compileClasspath --dependency org.apache.httpcomponents:httpclient to get more insight on how to solve the conflict.")
        failure.assertHasResolution("Debugging using the dependencyInsight report is described in more detail at: https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sec:identifying-reason-dependency-selection.")
    }

    def "constraints with different versions are properly sorted"() {
        given:
        buildFile("""
            dependencies {
                api("org.apache.httpcomponents:httpclient")
            }
        """)

        groovyFile("a/build.gradle", """
            dependencies {
                constraints {
                    api("org.apache.httpcomponents:httpclient") {
                        version {
                            strictly("4.5.11")
                        }
                    }
                }
            }
        """)

        groovyFile("b/b1/build.gradle", """
            dependencies {
                constraints {
                    api("org.apache.httpcomponents:httpclient") {
                        version {
                            strictly("4.5.10")
                        }
                    }
                }
            }
        """)

        groovyFile("b/b2/build.gradle", """
            dependencies {
                constraints {
                    api("org.apache.httpcomponents:httpclient") {
                        version {
                            strictly("4.5.8")
                        }
                    }
                }
            }
        """)

        groovyFile("c/build.gradle", """
            dependencies {
                constraints {
                    api("org.apache.httpcomponents:httpclient") {
                        version {
                            strictly("4.5")
                        }
                    }
                }
            }
        """)

        when:
        fails("forceResolution")

        then: "Has error output"
        failure.assertHasCause("Could not resolve all files for configuration ':compileClasspath'.")
        failure.assertHasCause("Could not resolve org.apache.httpcomponents:httpclient.")
        failure.assertHasCause("""Component is the target of multiple version constraints with conflicting requirements:
4.5 - directly in 'project :c' (apiElements)
4.5.8 - transitively via 'project :b' (apiElements)
4.5.10 - transitively via 'project :b' (apiElements)
4.5.11 - directly in 'project :a' (apiElements)""")

        and: "Helpful resolutions are provided"
        failure.assertHasResolution("Run with :dependencyInsight --configuration compileClasspath --dependency org.apache.httpcomponents:httpclient to get more insight on how to solve the conflict.")
        failure.assertHasResolution("Debugging using the dependencyInsight report is described in more detail at: https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sec:identifying-reason-dependency-selection.")
    }

    def "constraints with same version at different depths are property summarized"() {
        given:
        buildFile("""
            dependencies {
                api("org.apache.httpcomponents:httpclient")
            }
        """)

        groovyFile("a/build.gradle", """
            dependencies {
                constraints {
                    api("org.apache.httpcomponents:httpclient") {
                        version {
                            strictly("4.5.1")
                        }
                    }
                }
            }
        """)

        groovyFile("b/b1/build.gradle", """
            dependencies {
                constraints {
                    api("org.apache.httpcomponents:httpclient") {
                        version {
                            strictly("4.5.1")
                        }
                    }
                }
            }
        """)

        groovyFile("c/build.gradle", """
            dependencies {
                constraints {
                    api("org.apache.httpcomponents:httpclient") {
                        version {
                            strictly("4.5.2")
                        }
                    }
                }
            }
        """)

        when:
        fails("forceResolution")

        then: "Has error output"
        failure.assertHasCause("Could not resolve all files for configuration ':compileClasspath'.")
        failure.assertHasCause("Could not resolve org.apache.httpcomponents:httpclient.")
        failure.assertHasCause("""Component is the target of multiple version constraints with conflicting requirements:
4.5.1 - directly in 'project :a' (apiElements) (1 other path to this version)
4.5.2 - directly in 'project :c' (apiElements)""")

        and: "Helpful resolutions are provided"
        failure.assertHasResolution("Run with :dependencyInsight --configuration compileClasspath --dependency org.apache.httpcomponents:httpclient to get more insight on how to solve the conflict.")
        failure.assertHasResolution("Debugging using the dependencyInsight report is described in more detail at: https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sec:identifying-reason-dependency-selection.")
    }

    def setup() {
        given:
        settingsFile """
            dependencyResolutionManagement {
                repositories {
                    ${mavenCentralRepository()}
                }
            }

            include(":a", ":b", ":c")
            include(":a:a1", ":a:a2")
            include(":b:b1", ":b:b2")
            include(":c:c1", ":c:c2")
        """

        buildFile """
            plugins {
                id("java-library")
            }

            dependencies {
                api(project(":a"))
                api(project(":b"))
                api(project(":c"))
            }

            tasks.register("forceResolution") {
                inputs.files(configurations.named("compileClasspath"))

                doLast {
                    inputs.files.files.forEach { println(it) }
                }
            }

            subprojects {
                apply plugin: "java-library"
            }
        """

        groovyFile("a/build.gradle", """
            dependencies {
                api(project(":a:a1"))
                api(project(":a:a2"))
            }
        """)

        groovyFile("b/build.gradle", """
            dependencies {
                api(project(":b:b1"))
                api(project(":b:b2"))
            }
        """)

        groovyFile("c/build.gradle", """
            dependencies {
                api(project(":c:c1"))
                api(project(":c:c2"))
            }
        """)

        file("a/a1/build.gradle").touch()
        file("a/a2/build.gradle").touch()
        file("b/b1/build.gradle").touch()
        file("b/b2/build.gradle").touch()
        file("c/c1/build.gradle").touch()
        file("c/c2/build.gradle").touch()
    }
}
