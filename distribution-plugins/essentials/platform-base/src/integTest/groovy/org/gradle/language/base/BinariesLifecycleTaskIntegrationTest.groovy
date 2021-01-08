/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.base

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.internal.logging.text.DiagnosticsVisitor

@UnsupportedWithConfigurationCache(because = "software model")
class BinariesLifecycleTaskIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """rootProject.name = 'assemble-binary'"""
        buildFile << """
            plugins {
                id 'component-model-base'
            }
            import org.gradle.platform.base.internal.BinaryBuildAbility

            interface SampleBinary extends BinarySpec {
            }

            class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {
                @Override
                protected BinaryBuildAbility getBinaryBuildAbility() {
                    return new BinaryBuildAbility() {
                        @Override
                        public boolean isBuildable() {
                            return ! getName().contains("notBuildable");
                        }

                        @Override
                        public void explain(${DiagnosticsVisitor.name} visitor) {
                            visitor.node("Binary \${name} has 'notBuildable' in the name")
                        }
                    };
                }
            }

            class MySamplePlugin extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleBinary> builder) {
                    builder.defaultImplementation(DefaultSampleBinary)
                }
            }

            apply plugin:MySamplePlugin
        """
    }

    def "produces sensible error when there are component binaries and all are not buildable" () {
        withLibBinaries("notBuildableBinary1", "notBuildableBinary2")
        withStandaloneBinaries("ignoreMe")

        when:
        fails "assemble"

        then:
        failureDescriptionContains("Execution failed for task ':assemble'.")
        failure.assertHasCause("""No buildable binaries found:
  - SampleBinary 'lib:notBuildableBinary1':
      - Binary notBuildableBinary1 has 'notBuildable' in the name
  - SampleBinary 'lib:notBuildableBinary2':
      - Binary notBuildableBinary2 has 'notBuildable' in the name""")
    }

    def "builds those component binaries that are buildable and skips those that are not" () {
        withLibBinaries("buildableBinary1", "notBuildableBinary", "buildableBinary2")

        when:
        run "assemble"

        then:
        result.assertTasksExecuted(":libBuildableBinary1", ":libBuildableBinary2", ":assemble")

        when:
        run "build"

        then:
        result.assertTasksExecuted(":libBuildableBinary1", ":libBuildableBinary2", ":assemble", ":check", ":build")
    }

    def "does not produce error when assemble task has other dependencies" () {
        withLibBinaries("notBuildableBinary")
        buildFile << """
            task someOtherTask
            assemble.dependsOn someOtherTask
        """

        when:
        run "assemble"

        then:
        result.assertTasksExecutedInOrder(":someOtherTask", ":assemble")
    }

    def "does not do anything when the project is empty" () {
        when:
        run "assemble"

        then:
        result.assertTasksExecuted(":assemble")
        result.assertTasksSkipped(":assemble")
    }

    def "does not do anything when there are no component binaries" () {
        withStandaloneBinaries("ignoreMe")

        when:
        run "assemble"

        then:
        result.assertTasksExecuted(":assemble")
        result.assertTasksSkipped(":assemble")
    }

    def "builds only those binaries that belong to a component" () {
        withLibBinaries("buildableBinary", "notBuildableBinary")
        withStandaloneBinaries("ignoreMe1", "ignoreMe2")

        when:
        run "assemble"

        then:
        result.assertTasksExecuted(":libBuildableBinary", ":assemble")
    }

    def "check task does not build binaries" () {
        withLibBinaries("buildableBinary1", "notBuildableBinary", "buildableBinary2")
        withStandaloneBinaries("binary1", "binary2")

        when:
        run "check"

        then:
        result.assertTasksExecuted(":check")
    }

    def withLibBinaries(String... binaries) {
        buildFile << """
            model {
                components {
                    lib(LibrarySpec) {
                    }
                }
            }
"""
        for (String binaryName : binaries) {
            buildFile << """
                model {
                    components.lib.binaries {
                        $binaryName(SampleBinary)
                    }
                }
"""
        }
    }

    def withStandaloneBinaries(String... binaryNames) {
        for (String binaryName : binaryNames) {
            buildFile << """
                model {
                    binaries {
                        $binaryName(SampleBinary)
                    }
                }
            """
        }
    }
}
