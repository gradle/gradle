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

class AssembleBinariesTaskIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """rootProject.name = 'assemble-binary'"""
        buildFile << """
            plugins {
                id 'language-base'
            }
        """
    }

    def "produces sensible error when no binaries are buildable" () {
        withNotBuildableBinary()

        when:
        fails "assemble"

        then:
        failureDescriptionContains("Execution failed for task ':assemble'.")
        failureHasCause("No buildable binaries found.")
    }

    def "does not produce error when binaries are buildable" () {
        withBuildableBinary()

        expect:
        succeeds "assemble"
    }

    def "does not produce error when assemble task has other dependencies" () {
        withNotBuildableBinary()
        buildFile << """
            task someOtherTask
            assemble.dependsOn someOtherTask
        """

        expect:
        succeeds "assemble"
    }

    def "does not produce error when no binaries are configured" () {
        expect:
        succeeds "assemble"
    }

    def withSampleBinary() {
        buildFile << """
            import org.gradle.model.*
            import org.gradle.model.collection.*

            interface SampleBinary extends BinarySpec {
                String getVersion()
                void setVersion(String version)
            }
        """
    }

    def withSampleBinaryPlugin() {
        buildFile << """
            class MySamplePlugin implements Plugin<Project> {
                void apply(final Project project) {}

                static class Rules extends RuleSource {
                    @BinaryType
                    void register(BinaryTypeBuilder<SampleBinary> builder) {
                        builder.defaultImplementation(DefaultSampleBinary)
                    }

                    @Mutate
                    void createSampleBinary(CollectionBuilder<SampleBinary> binarySpecs) {
                        println "creating binary"
                        binarySpecs.create("sampleBinary")
                    }
                }
            }

            apply plugin:MySamplePlugin
        """
    }

    def withBuildableBinary() {
        withSampleBinary()
        buildFile << """
            class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {
                String version
                boolean isBuildable() {
                    return true
                }
            }
        """
        withSampleBinaryPlugin()
    }

    def withNotBuildableBinary() {
        withSampleBinary()
        buildFile << """
            class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {
                String version
                boolean isBuildable() {
                    return false
                }
            }
        """
        withSampleBinaryPlugin()
    }
}
