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

package org.gradle.nativebinaries.language.objectivec

import org.gradle.nativebinaries.language.cpp.AbstractLanguageIncrementalCompileIntegrationTest
import org.gradle.nativebinaries.language.cpp.fixtures.app.IncrementalHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.ObjectiveCHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.NOT_WINDOWS)
class ObjectiveCLanguageIncrementalCompileIntegrationTest extends AbstractLanguageIncrementalCompileIntegrationTest {
    @Override
    IncrementalHelloWorldApp getHelloWorldApp() {
        return new ObjectiveCHelloWorldApp()
    }

    def "recompiles only source file that imported changed header file"() {
        given:
        sourceFile << """
            #import "${otherHeaderFile.name}"
"""
        and:
        initialCompile()

        when:
        otherHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        recompiled sourceFile
    }

    def "source is always recompiled if it imported header via macro"() {
        given:
        sourceFile << """
            #define MY_HEADER "${otherHeaderFile.name}"
            #import MY_HEADER
"""

        and:
        initialCompile()

        when:
        otherHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        recompiled sourceFile

        when: "Header that is NOT included is changed"
        file("src/main/headers/notIncluded.h") << """
            // Dummy header file
"""
        and:
        run "mainExecutable"

        then: "Source is still recompiled"
        executedAndNotSkipped compileTask

        and:
        recompiled sourceFile
    }

    def "recompiles source file when transitively imported header file is changed"() {
        given:
        def transitiveHeaderFile = file("src/main/headers/transitive.h") << """
           // Dummy header file
"""
        otherHeaderFile << """
            #import "${transitiveHeaderFile.name}"
"""
        sourceFile << """
            #import "${otherHeaderFile.name}"
"""

        and:
        initialCompile()

        when:
        transitiveHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        recompiled sourceFile
    }
}