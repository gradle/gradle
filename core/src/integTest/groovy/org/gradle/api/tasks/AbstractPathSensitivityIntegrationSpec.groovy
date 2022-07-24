/*
 * Copyright 2016 the original author or authors.
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

import static org.gradle.api.tasks.PathSensitivity.ABSOLUTE
import static org.gradle.api.tasks.PathSensitivity.NAME_ONLY
import static org.gradle.api.tasks.PathSensitivity.NONE
import static org.gradle.api.tasks.PathSensitivity.RELATIVE

abstract class AbstractPathSensitivityIntegrationSpec extends AbstractIntegrationSpec {

    def "single source file renamed with #pathSensitive as input is loaded from cache: #expectedOutcome"() {
        given:
        file("sources/input.txt").text = "input"

        declareTestTaskWithPathSensitivity(pathSensitive)

        buildFile << """
            test {
                sources = files("sources")
            }
        """

        when:
        execute "test"
        then:
        result.assertTaskNotSkipped(":test")

        when:
        assert file("sources/input.txt").renameTo(file("sources/input-renamed.txt"))

        cleanWorkspace()

        execute "test"
        then:
        result.groupedOutput.task(":test").outcome == expectedOutcome

        where:
        pathSensitive | expectedOutcome
        ABSOLUTE      | null
        RELATIVE      | null
        NAME_ONLY     | null
        NONE          | statusForReusedOutput
    }

    def "single source file moved within hierarchy with #pathSensitive as input is loaded from cache: #expectedOutcome"() {
        given:
        file("src/data1").createDir()
        file("src/data2").createDir()
        file("src/data1/input.txt").text = "input"

        declareTestTaskWithPathSensitivity(pathSensitive)

        buildFile << """
            test {
                sources = fileTree("src")
            }
        """

        when:
        execute "test"
        then:
        result.assertTaskNotSkipped(":test")

        when:
        assert file("src/data1/input.txt").renameTo(file("src/data2/input.txt"))
        cleanWorkspace()

        execute "test"
        then:
        result.groupedOutput.task(":test").outcome == expectedOutcome

        where:
        pathSensitive | expectedOutcome
        ABSOLUTE      | null
        RELATIVE      | null
        NAME_ONLY     | statusForReusedOutput
        NONE          | statusForReusedOutput
    }

    def "source file hierarchy moved with #pathSensitive as input is loaded from cache: #expectSkipped"() {
        given:
        file("src/data/input.txt").text = "input"

        declareTestTaskWithPathSensitivity(pathSensitive)

        buildFile << """
            test {
                sources = files("src")
            }
        """

        when:
        execute "test"
        then:
        result.assertTaskNotSkipped(":test")

        when:
        assert file("src").renameTo(file("source"))
        buildFile << """
            test {
                sources = files("source")
            }
        """

        cleanWorkspace()

        execute "test"
        then:
        result.groupedOutput.task(":test").outcome == expectSkipped

        where:
        pathSensitive | expectSkipped
        ABSOLUTE      | null
        RELATIVE      | statusForReusedOutput
        NAME_ONLY     | statusForReusedOutput
        NONE          | statusForReusedOutput
    }

    abstract void execute(String... tasks)

    abstract void cleanWorkspace()

    abstract String getStatusForReusedOutput()

    private void declareTestTaskWithPathSensitivity(PathSensitivity pathSensitivity) {
        file("buildSrc/src/main/groovy/TestTask.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.file.*
            import org.gradle.api.tasks.*

            @CacheableTask
            class PathSensitiveTask extends DefaultTask {
                @InputFiles
                @PathSensitive(PathSensitivity.${pathSensitivity.name()})
                FileCollection sources

                @OutputFile
                File outputFile

                @TaskAction
                def exec() {
                    outputFile.text = sources*.name.join("\\n")
                }
            }
        """
        buildFile << """
            task test(type: PathSensitiveTask) {
                outputFile = file("build/output.txt")
            }
        """
    }

    def "copy task stays up-to-date after files are moved but end up copied to the same destination"() {
        file("src/data/input.txt").text = "data"

        when:
        buildFile << """
            task copy(type: Copy) {
                outputs.cacheIf { true }
                from "src"
                into "target"
            }
        """

        execute "copy"
        then:
        executedAndNotSkipped ":copy"

        assert file("src").renameTo(file("source"))

        when:
        cleanWorkspace()

        buildFile.text = """
            task copy(type: Copy) {
                outputs.cacheIf { true }
                from "source"
                into "target"
            }
        """

        execute "copy"
        then:
        skipped ":copy"
    }

    def "copy task is not up-to-date when files end up copied to a different destination"() {
        file("src/data/input.txt").text = "data"

        when:
        buildFile << """
            task copy(type: Copy) {
                outputs.cacheIf { true }
                from "src"
                into "target"
            }
        """

        execute "copy"
        then:
        executedAndNotSkipped ":copy"

        assert file("src/data/input.txt").renameTo(file("src/data/input-renamed.txt"))

        when:
        cleanWorkspace()
        execute "copy"
        then:
        executedAndNotSkipped ":copy"
    }
}
