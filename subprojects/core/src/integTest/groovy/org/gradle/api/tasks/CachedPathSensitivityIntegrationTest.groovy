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

import org.gradle.integtests.fixtures.executer.GradleExecuter
import spock.lang.Unroll

import static org.gradle.api.tasks.PathSensitivity.*

class CachedPathSensitivityIntegrationTest extends AbstractCachedTaskExecutionIntegrationSpec {

    @Unroll("single source file renamed with #sensitivity as input is loaded from cache: #expectLoadedFromCache")
    def "single source file renamed"() {
        given:
        file("input.txt").text = "input"

        declareTestTaskWithPathSensitivity(sensitivity)

        buildFile << """
            test {
                sources = files("input.txt")
            }
        """

        when:
        succeedsWithCache "test"
        then:
        skippedTasks.empty

        file("input.txt").renameTo(file("input-renamed.txt"))

        buildFile << """
            test {
                sources = files("input-renamed.txt")
            }
        """

        when:
        succeedsWithCache "test"
        then:
        skippedTasks.empty == !expectLoadedFromCache

        where:
        sensitivity | expectLoadedFromCache
        ABSOLUTE    | false
        RELATIVE    | false
        NAME_ONLY   | false
        NONE        | true
    }

    @Unroll("single source file moved within hierarchy with #sensitivity as input is loaded from cache: #expectLoadedFromCache")
    def "single source file moved within hierarchy"() {
        given:
        file("src", "data").createDir()
        file("src", "other-data").createDir()
        file("src/data/input.txt").text = "input"

        declareTestTaskWithPathSensitivity(sensitivity)

        buildFile << """
            test {
                sources = files("src")
            }
        """

        when:
        succeedsWithCache "test"
        then:
        skippedTasks.empty

        file("src/data/input.txt").moveToDirectory(file("src/other-data"))

        when:
        succeedsWithCache "test"
        then:
        skippedTasks.empty == !expectLoadedFromCache

        where:
        sensitivity | expectLoadedFromCache
        ABSOLUTE    | false
        RELATIVE    | false
        NAME_ONLY   | true
        NONE        | true
    }

    @Unroll("source file hierarchy moved with #sensitivity as input is loaded from cache: #expectLoadedFromCache")
    def "source file hierarchy moved"() {
        given:
        file("src", "data").createDir()
        file("src/data/input.txt").text = "input"

        declareTestTaskWithPathSensitivity(sensitivity)

        buildFile << """
            test {
                sources = files("src")
            }
        """

        when:
        succeedsWithCache "test"
        then:
        skippedTasks.empty

        file("src").renameTo(file("source"))
        buildFile << """
            test {
                sources = files("source")
            }
        """

        when:
        succeedsWithCache "test"
        then:
        skippedTasks.empty == !expectLoadedFromCache

        where:
        sensitivity | expectLoadedFromCache
        ABSOLUTE    | false
        RELATIVE    | true
        NAME_ONLY   | true
        NONE        | true
    }

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
                outputFile = file("output.txt")
            }
        """
    }

    def succeedsWithCache(String... tasks) {
        enableCache()
        succeeds tasks
    }

    private GradleExecuter enableCache() {
        executer.withArgument "-Dorg.gradle.cache.tasks=true"
        executer.withArgument "-Dorg.gradle.cache.tasks.directory=" + cacheDir.absolutePath
    }
}
