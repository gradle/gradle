/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.file

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class ManagedFilePropertyGroovyInterOpIntegrationTest extends AbstractFilePropertyGroovyInterOpIntegrationTest {
    @Override
    void taskDefinition() {
        pluginDir.file("src/main/groovy/ProducerTask.groovy") << """
            import ${DefaultTask.name}
            import ${RegularFileProperty.name}
            import ${TaskAction.name}
            import ${OutputFile.name}

            abstract class ProducerTask extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutFile()

                @TaskAction
                def run() {
                    outFile.get().asFile.text = "content"
                }
            }
        """
    }

    @Override
    void taskWithNestedBeanDefinition() {
        pluginDir.file("src/main/groovy/ProducerTask.groovy") << """
            import ${DefaultTask.name}
            import ${TaskAction.name}
            import ${Nested.name}

            abstract class ProducerTask extends DefaultTask {
                @Nested
                abstract Params getParams()

                @TaskAction
                def run() {
                    params.outFile.get().asFile.text = "content"
                }
            }
        """
    }
}
