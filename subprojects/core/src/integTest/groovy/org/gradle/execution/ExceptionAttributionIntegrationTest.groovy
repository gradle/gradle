/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.execution

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ExceptionAttributionIntegrationTest extends AbstractIntegrationSpec {
    def "blames build script for an exception thrown in the script body"() {
        buildFile << """
            task broken {
                doLast {
                    throw new RuntimeException("broken")
                }
            }
        """

        expect:
        2.times {
            fails("broken")
            failure.assertHasFileName("Build file '$buildFile'")
            failure.assertHasLineNumber(4)
            failure.assertHasFailure("Execution failed for task ':broken'.") {
                failureHasCause("broken")
            }
        }
    }

    def "blames build script for an exception thrown when applying a plugin"() {
        file("buildSrc/src/main/groovy/PluginImpl.groovy") << """
            import ${Project.name}
            import ${Plugin.name}

            class PluginImpl implements Plugin<Project> {
                void apply(Project p) {
                    throw new RuntimeException("broken")
                }
            }
        """
        file("buildSrc/src/main/resources/META-INF/gradle-plugins/test.broken.properties") << """
            implementation-class=PluginImpl
        """
        buildFile << """
            plugins {
                id("test.broken")
            }
        """

        expect:
        2.times {
            fails("broken")
            failure.assertHasFileName("Build file '$buildFile'")
            failure.assertHasLineNumber(3)
            failure.assertHasFailure("An exception occurred applying plugin request [id: 'test.broken']") {
                failureHasCause("broken")
            }
        }
    }

    def "blames build script for a validation exception thrown by a plugin"() {
        file("buildSrc/src/main/groovy/PluginImpl.groovy") << """
            import ${Project.name}
            import ${Plugin.name}

            class PluginImpl implements Plugin<Project> {
                void apply(Project p) {
                    p.extensions.create("thing", ExtensionImpl)
                }
            }

            class ExtensionImpl {
                String getProp() { return "value" }
                void setProp(String value) {
                    throw new RuntimeException("broken")
                }
            }
        """
        file("buildSrc/src/main/resources/META-INF/gradle-plugins/test.broken.properties") << """
            implementation-class=PluginImpl
        """
        buildFile << """
            plugins {
                id("test.broken")
            }

            task broken {
                def t = thing
                doLast {
                    t.prop = "new value"
                }
            }
        """

        expect:
        2.times {
            fails("broken")
            failure.assertHasFileName("Build file '$buildFile'")
            failure.assertHasLineNumber(9)
            failure.assertHasFailure("Execution failed for task ':broken'.") {
                failureHasCause("broken")
            }
        }
    }
}
