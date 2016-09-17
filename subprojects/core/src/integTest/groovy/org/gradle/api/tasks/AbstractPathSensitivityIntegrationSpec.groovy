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
import spock.lang.Unroll

import static org.gradle.api.tasks.PathSensitivity.*

abstract class AbstractPathSensitivityIntegrationSpec extends AbstractIntegrationSpec {

    @Unroll("single source file renamed with #pathSensitive as input is loaded from cache: #expectSkipped (order sensitive: #orderSensitive)")
    def "single source file renamed"() {
        given:
        file("sources/input.txt").text = "input"

        declareTestTaskWithPathSensitivity(pathSensitive, orderSensitive)

        buildFile << """
            test {
                sources = files("sources")
            }
        """

        when:
        execute "test"
        then:
        skippedTasks.empty

        when:
        assert file("sources/input.txt").renameTo(file("sources/input-renamed.txt"))

        cleanWorkspace()
        execute "test"
        then:
        skippedTasks.empty == !expectSkipped

        where:
        pathSensitive | orderSensitive | expectSkipped
        ABSOLUTE      | true           | false
        ABSOLUTE      | false          | false
        RELATIVE      | true           | false
        RELATIVE      | false          | false
        NAME_ONLY     | true           | false
        NAME_ONLY     | false          | false
        NONE          | true           | true
        NONE          | false          | true
    }

    @Unroll("single source file moved within hierarchy with #pathSensitive as input is loaded from cache: #expectSkipped (order sensitive: #orderSensitive)")
    def "single source file moved within hierarchy"() {
        given:
        file("src/data1").createDir()
        file("src/data2").createDir()
        file("src/data1/input.txt").text = "input"

        declareTestTaskWithPathSensitivity(pathSensitive, orderSensitive)

        buildFile << """
            test {
                sources = fileTree("src")
            }
        """

        when:
        execute "test"
        then:
        skippedTasks.empty

        when:
        assert file("src/data1/input.txt").renameTo(file("src/data2/input.txt"))
        cleanWorkspace()
        execute "test"
        then:
        skippedTasks.empty == !expectSkipped

        where:
        pathSensitive | orderSensitive | expectSkipped
        ABSOLUTE      | true           | false
        ABSOLUTE      | false          | false
        RELATIVE      | true           | false
        RELATIVE      | false          | false
        NAME_ONLY     | true           | false
        NAME_ONLY     | false          | true
        NONE          | true           | true
        NONE          | false          | true

        // NOTE: NAME_ONLY in order-sensitive mode is not skipped,
        // because the order of files and directories do change from:
        //  - data1, data1/input.txt, data2
        // to
        //  - data1, data2, data2/input.txt
        //
        // It's not an issue for the NONE case, because there we
        // ignore directories, as they have no contents to be hashed.
    }

    @Unroll("source file hierarchy moved with #pathSensitive as input is loaded from cache: #expectSkipped (order sensitive: #orderSensitive)")
    def "source file hierarchy moved"() {
        given:
        file("src/data/input.txt").text = "input"

        declareTestTaskWithPathSensitivity(pathSensitive, orderSensitive)

        buildFile << """
            test {
                sources = files("src")
            }
        """

        when:
        execute "test"
        then:
        skippedTasks.empty

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
        skippedTasks.empty == !expectSkipped

        where:
        pathSensitive | orderSensitive | expectSkipped
        ABSOLUTE      | true           | false
        ABSOLUTE      | false          | false
        RELATIVE      | true           | true
        RELATIVE      | false          | true
        NAME_ONLY     | true           | true
        NAME_ONLY     | false          | true
        NONE          | true           | true
        NONE          | false          | true
    }

    abstract void execute(String... tasks)

    abstract void cleanWorkspace()

    private void declareTestTaskWithPathSensitivity(PathSensitivity pathSensitivity, boolean orderSensitive) {
        file("buildSrc/src/main/groovy/TestTask.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.file.*
            import org.gradle.api.tasks.*

            @CacheableTask
            class PathSensitiveTask extends DefaultTask {
                @InputFiles
                ${orderSensitive ? "@OrderSensitive" : ""}
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
                outputFile = file("output.txt")
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
        skippedTasks.empty

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
        skippedTasks as List == [":copy"]
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
        skippedTasks.empty

        assert file("src/data/input.txt").renameTo(file("src/data/input-renamed.txt"))

        when:
        cleanWorkspace()
        execute "copy"
        then:
        skippedTasks.empty
    }
}
