/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.resource

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.archives.TestReproducibleArchives
import org.junit.Rule

@TestReproducibleArchives
class TextResourceIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    TestResources resource = new TestResources(temporaryFolder)

    def "string backed text resource"() {
        when:
        run("stringText")

        then:
        executedTasks == [":stringText"]
        file("output.txt").text == "my config"

        when:
        run("stringText")

        then:
        skippedTasks == [":stringText"] as Set
    }

    def "file backed text resource"() {
        when:
        run("generateConfigFile")
        run("fileText")

        then:
        executedTasks == [":fileText"]
        file("output.txt").text == "my config"

        when:
        run("fileText")

        then:
        skippedTasks == [":fileText"] as Set
    }

    def "single-element file collection backed text resource"() {
        when:
        run("fileCollectionText")

        then:
        executedTasks == [":generateConfigFile", ":fileCollectionText"]
        file("output.txt").text == "my config"

        when:
        run("fileCollectionText")

        then:
        skippedTasks == [":generateConfigFile", ":fileCollectionText"] as Set
    }

    def "archive entry backed text resource"() {
        when:
        run("archiveEntryText")

        then:
        executedTasks == [":generateConfigFile", ":generateConfigZip", ":archiveEntryText"]
        file("output.txt").text == "my config"

        when:
        run("archiveEntryText")

        then:
        skippedTasks == [":generateConfigFile", ":generateConfigZip", ":archiveEntryText"] as Set
    }
}
