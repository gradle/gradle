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

package org.gradle.language.base.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class LifecycleBasePluginIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
        apply plugin:org.gradle.language.base.plugins.LifecycleBasePlugin
        """
    }

    @Unroll
    def "throws deprecation warning when applied in build with #taskName"() {
        when:
        buildFile << """

        task $taskName << {
            println "custom $taskName task"
        }
        """
        executer.withDeprecationChecksDisabled()
        succeeds(taskName)
        then:
        output.contains("Defining custom '$taskName' task when using the standard Gradle lifecycle plugins has been deprecated and is scheduled to be removed")
        where:
        taskName << ["check", "clean", "build", "assemble"]
    }

    def "can attach custom task as dependency to lifecycle task - #task"() {
        when:
        buildFile << """
            task myTask {}
            ${taskName}.dependsOn myTask
        """

        then:
        succeeds(taskName)
        ":myTask" in executedTasks

        where:
        taskName << ["check", "build"]
    }

    def "binaries are built when build task execution is requested"() {
        buildFile << """
            import org.gradle.model.ModelMap

            interface SampleBinary extends BinarySpec {
            }

            class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {
            }

            class SampleBinaryPlugin extends RuleSource {
                @BinaryType
                void register(BinaryTypeBuilder<SampleBinary> builder) {
                    builder.defaultImplementation(DefaultSampleBinary)
                }

                @Mutate
                void createSampleBinary(ModelMap<SampleBinary> binarySpecs) {
                    binarySpecs.create("sampleBinary")
                }
            }

            apply plugin: SampleBinaryPlugin
        """

        when:
        succeeds "build"

        then:
        ":sampleBinary" in executedTasks
    }
}
