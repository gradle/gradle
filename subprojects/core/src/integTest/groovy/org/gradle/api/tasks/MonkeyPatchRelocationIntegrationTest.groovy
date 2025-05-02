/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.test.fixtures.file.TestFile

class MonkeyPatchRelocationIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    def "can change task input's path sensitivity"() {
        def original = testDirectory.file("original")
        def relocated = testDirectory.file("relocated")

        setupProjectIn(original)
        setupProjectIn(relocated)

        inDirectory(original)
        withBuildCache().run "broken"

        when:
        inDirectory(relocated)
        withBuildCache().run "broken"
        then:
        skipped ":broken"
    }

    private void setupProjectIn(TestFile dir) {
        dir.file("settings.gradle") << localCacheConfiguration()

        dir.file("input.txt") << "data"

        dir.file("build.gradle") << """
            @CacheableTask
            class Broken extends DefaultTask {
                FileCollection processorListFile = project.layout.files("input.txt")

                @InputFiles
                @PathSensitive(PathSensitivity.NONE)
                FileCollection getProcessorListFile() {
                    this.processorListFile
                }

                @OutputFile
                File outputFile = new File(temporaryDir, "output")

                @TaskAction
                void doSomething() {
                    outputFile.text = "done"
                }
            }

            task broken(type: Broken) {
                def originalValue = processorListFile
                processorListFile = files()
                inputs.files(originalValue)
                    .withPathSensitivity(PathSensitivity.NONE)
                    .withPropertyName("processorListFileHack")

                doFirst {
                    processorListFile = originalValue
                }
            }
        """
    }
}
