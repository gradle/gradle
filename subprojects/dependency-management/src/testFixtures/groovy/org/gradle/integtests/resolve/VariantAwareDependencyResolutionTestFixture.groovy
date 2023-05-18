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

package org.gradle.integtests.resolve

import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.test.fixtures.file.TestFile

trait VariantAwareDependencyResolutionTestFixture extends TasksWithInputsAndOutputs {
    abstract TestFile getBuildFile()

    def setupBuildWithColorVariants(TestFile buildFile = getBuildFile()) {
        buildFile << """
            def color = Attribute.of('color', String)
            allprojects {
                configurations {
                    implementation {
                        // TODO: Make this a bucket
                        // canBeResolved = false
                        // canBeConsumed = false
                    }
                    resolver {
                        attributes.attribute(color, 'blue')
                        extendsFrom implementation
                        assert canBeResolved
                        canBeConsumed = false
                    }
                    outgoing {
                        attributes.attribute(color, 'blue')
                        extendsFrom implementation
                        canBeResolved = false
                        assert canBeConsumed
                    }
                }
            }

            class ShowFileCollection extends DefaultTask {
                @InputFiles
                final ConfigurableFileCollection files = project.objects.fileCollection()

                ShowFileCollection() {
                    outputs.upToDateWhen { false }
                }

                @TaskAction
                def go() {
                    println "result = \${files.files.name}"
                }
            }
        """
    }
}
