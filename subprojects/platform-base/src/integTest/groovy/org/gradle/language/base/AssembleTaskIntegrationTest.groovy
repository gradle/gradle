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
import org.hamcrest.Matchers

class AssembleTaskIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """rootProject.name = 'assemble-binary'"""
        buildFile << """
            plugins {
                id 'language-base'
            }
        """
    }

    def "is up to date when there are no not buildable binaries"() {
        withBinaries("sampleBinary")

        when:
        succeeds "assemble"

        then:
        skipped ":assemble"
    }

    def "is up to date when there are both buildable and not buildable binaries"() {
        withBinaries("sampleBinary", "notBuildableBinary")

        when:
        succeeds "assemble"

        then:
        skipped ":assemble"
    }

    def "produces sensible error when no binaries are buildable" () {
        withBinaries("notBuildableBinary1", "notBuildableBinary2")

        when:
        fails "assemble"

        then:
        failureDescriptionContains("Execution failed for task ':assemble'.")
        failure.assertThatCause(Matchers.<String>allOf(
            Matchers.startsWith("No buildable binaries found:"),
            Matchers.containsString("notBuildableBinary1: Binary notBuildableBinary1 has 'notBuildable' in the name"),
            Matchers.containsString("notBuildableBinary2: Binary notBuildableBinary2 has 'notBuildable' in the name")
        ))
    }

    def "does not produce error when binaries are buildable" () {
        withBinaries("buildableBinary1", "notBuildableBinary", "buildableBinary2")

        expect:
        succeeds "assemble"
        skipped ":buildableBinary1", ":buildableBinary2"
        notExecuted ":notBuildableBinary"
    }

    def "does not produce error when assemble task has other dependencies" () {
        withBinaries("notBuildableBinary")
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
            import org.gradle.platform.base.internal.BinaryBuildAbility

            interface SampleBinary extends BinarySpec {
            }
        """
    }

    def withSampleBinaryPlugin(String... binaries) {
        buildFile << """
            class MySamplePlugin implements Plugin<Project> {
                void apply(final Project project) {}

                static class Rules extends RuleSource {
                    @BinaryType
                    void register(BinaryTypeBuilder<SampleBinary> builder) {
                        builder.defaultImplementation(DefaultSampleBinary)
                    }

                    @Mutate
                    void createSampleBinary(ModelMap<SampleBinary> binarySpecs) {
                        ${generateBinaries(binaries)}
                    }
                }
            }

            apply plugin:MySamplePlugin
        """
    }

    def generateBinaries(binaries) {
        return binaries.collect { "binarySpecs.create(\"${it}\")" }.join("\n")
    }

    def withBinaries(String... binaries) {
        withSampleBinary()
        buildFile << """
            class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {
                @Override
                protected BinaryBuildAbility getBinaryBuildAbility() {
                    return new BinaryBuildAbility() {
                        @Override
                        public boolean isBuildable() {
                            return ! getName().contains("notBuildable");
                        }

                        @Override
                        public void explain(TreeVisitor<? super String> visitor) {
                            visitor.node("Binary \${getName()} has 'notBuildable' in the name")
                        }
                    };
                }
            }
        """
        withSampleBinaryPlugin(binaries)
    }
}
