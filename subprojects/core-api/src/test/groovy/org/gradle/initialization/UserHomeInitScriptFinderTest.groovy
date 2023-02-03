/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.initialization

import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory
class UserHomeInitScriptFinderTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    private UserHomeInitScriptFinder finder

    def setup() {
        finder = new UserHomeInitScriptFinder(temporaryFolder.getTestDirectory())
    }

    def "adds user #initScriptName init script when it exits"() {
        given:
        def initScript = temporaryFolder.createFile(initScriptName)
        def sourceList = []

        when:
        finder.findScripts(sourceList)

        then:
        sourceList.size == 1
        sourceList[0] == initScript

        where:
        initScriptName << ['init.gradle', 'init.gradle.kts']
    }

    def "does not add user init script when none exists"() {
        given:
        def sourceList = []

        when:
        finder.findScripts(sourceList)

        then:
        sourceList.isEmpty()
    }

    def "adds init scripts from init directory when it exists."() {
        given:
        def initScript = temporaryFolder.createFile("init.d/script.gradle")
        def secondScript = temporaryFolder.createFile("init.d/another.gradle.kts")
        def sourceList = []

        when:
        finder.findScripts(sourceList)

        then:
        sourceList.size() == 2
        sourceList as Set == [secondScript, initScript] as Set
    }
}
