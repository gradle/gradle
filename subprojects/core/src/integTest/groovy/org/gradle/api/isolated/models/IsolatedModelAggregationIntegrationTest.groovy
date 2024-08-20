/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.isolated.models

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class IsolatedModelAggregationIntegrationTest extends AbstractIntegrationSpec {

    def "can aggregate task outputs from all projects and process them in root project task"() {
        settingsFile """
            rootProject.name = "root"
            include(":sub")

            abstract class SomeTask extends DefaultTask {
                @Input
                abstract Property<Integer> getNumber()

                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction run() {
                    output.get().asFile.text = number.get().toString()
                }
            }

            gradle.lifecycle.beforeProject { project ->
                def numberTask = project.tasks.register("number", SomeTask) {
                    number = project.name.length()
                    output = project.layout.buildDirectory.file("out.txt")
                }

                Provider<RegularFile> taskOutput = numberTask.flatMap { it.output }
                def router = project.services.get(org.gradle.internal.isolated.models.IsolatedModelRouter)
                router.postModel(router.key("numberFile", RegularFile), router.work(taskOutput))
            }
        """

        createDirs("sub")

        buildFile """
            abstract class SummingTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getNumbers()

                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction run() {
                    def sum = numbers.files.collect {
                        it.text.toInteger()
                    }.sum() as Integer

                    output.get().asFile.text = sum.toString()
                }
            }

            def router = project.services.get(org.gradle.internal.isolated.models.IsolatedModelRouter)



            tasks.register("sum", SummingTask) {
                Map<String, Provider<RegularFile>> numberFiles =
                    router.getProjectModels(router.key("numberFile", RegularFile), allprojects.collect { it.path })
                numbers = files(numberFiles.values())
                output = layout.buildDirectory.file("sum.txt")
            }
        """

        when:
        run "sum"

        then:

        def outFile = file("build/sum.txt")
        outFile.exists()
        outFile.text == "7"
    }

}
