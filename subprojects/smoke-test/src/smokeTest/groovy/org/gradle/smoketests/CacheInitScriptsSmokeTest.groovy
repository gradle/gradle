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

package org.gradle.smoketests

import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE

class CacheInitScriptsSmokeTest extends AbstractSmokeTest {

    def 'cache init script plain text output'() {
        given:
        def expectedLines = """
            Overlapping task outputs while executing 'build':

              No tasks with overlapping outputs found
        """.stripIndent().readLines().findAll()

        useSample("cache-init-scripts")

        // Prepare the cache
        runner('-I', 'taskCacheInit.gradle', 'build').build()
        runner('clean').build()

        when:
        def result = runner('-I', 'taskCacheInit.gradle', 'build').build()
        println result.output
        def actualLines = result.output.readLines()

        then:
        result.task(':compileJava').outcome == FROM_CACHE
        expectedLines.each { expectedLine ->
            assert actualLines.contains(expectedLine)
        }
    }
}
