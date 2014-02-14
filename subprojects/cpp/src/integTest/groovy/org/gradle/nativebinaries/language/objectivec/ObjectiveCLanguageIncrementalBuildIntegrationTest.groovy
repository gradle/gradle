/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.language.cpp.AbstractLanguageIncrementalBuildIntegrationTest
import org.gradle.nativebinaries.language.cpp.fixtures.app.IncrementalHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.ObjectiveCHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.NOT_WINDOWS)
class ObjectiveCLanguageIncrementalBuildIntegrationTest extends AbstractLanguageIncrementalBuildIntegrationTest{

    def setupSpec(){
        multiPlatformsAvailable = OperatingSystem.current().isMacOsX();
    }

    def "recompiles binary when imported header file changes"() {
        sourceFile.text = sourceFile.text.replaceFirst('#include "hello.h"', "#import \"hello.h\"")

        given:
        run "installMainExecutable"


        when:
        headerFile << """
            int unused();
"""
        sleep(1000)
        run "mainExecutable"

        then:
        executedAndNotSkipped libraryCompileTask
        executedAndNotSkipped mainCompileTask

        if (isClangOnNonOsxWithObjectiveC()) {
            executedAndNotSkipped ":linkHelloSharedLibrary", ":helloSharedLibrary"
            executedAndNotSkipped ":linkMainExecutable", ":mainExecutable"
        } else {
            skipped ":linkHelloSharedLibrary", ":helloSharedLibrary"
            skipped ":linkMainExecutable", ":mainExecutable"
        }
    }

    @Override
    IncrementalHelloWorldApp getHelloWorldApp() {
        return new ObjectiveCHelloWorldApp()
    }
}
