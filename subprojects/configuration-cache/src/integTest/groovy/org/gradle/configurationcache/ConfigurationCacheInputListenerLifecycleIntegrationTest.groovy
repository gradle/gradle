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

package org.gradle.configurationcache


import org.gradle.initialization.StartParameterBuildOptions

class ConfigurationCacheInputListenerLifecycleIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "configuration inputs are tracked during task graph serialization"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        and: "a project that has a provider having undeclared configuration inputs, which is linked to a task"
        buildFile("""
            abstract class MyTask extends DefaultTask {
                @Input
                abstract Property<String> getMyProperty();

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void doWork() {
                    getOutputFile().asFile.get().write(getMyProperty().get())
                }
            }

            def touchFsInEvaluation = provider {
                file("test").exists() ? "yes" : "no"
            }

            tasks.register("myTask", MyTask.class) {
                myProperty = touchFsInEvaluation
                outputFile = project.layout.buildDirectory.file("out.txt")
            }
        """)
        if (isOptOut) {
            file("gradle.properties") << "$IGNORE_INPUTS_PROPERTY=true"
        }

        when:
        configurationCacheRun "myTask"

        then:
        configurationCache.assertStateStored()
        testDirectory.file("build/out.txt").text == "no"

        when: "the file that is used in the undeclared configuration input changes and the build runs again"
        testDirectory.file("test").createNewFile()
        configurationCacheRun "myTask"

        then: "the cache entry is invalidated because of the file system input"
        if (!isOptOut) {
            configurationCache.assertStateStored()
            testDirectory.file("build/out.txt").text == "yes"
        } else {
            // This is incorrect behavior of configuration cache that is a result of using the opt-out flag
            configurationCache.assertStateLoaded()
            testDirectory.file("build/out.txt").text == "no"
        }

        where:
        isOptOut | _
        true     | _
        false    | _
    }

    def 'switching the opt-out flag should invalidate the configuration cache entry'() {
        when:
        configurationCacheRun()
        configurationCacheRun("-D$IGNORE_INPUTS_PROPERTY=true")

        then:
        outputContains("the set of ignored configuration inputs has changed")
    }

    private static final String IGNORE_INPUTS_PROPERTY = StartParameterBuildOptions.ConfigurationCacheIgnoreInputsInTaskGraphSerialization.PROPERTY_NAME
}
