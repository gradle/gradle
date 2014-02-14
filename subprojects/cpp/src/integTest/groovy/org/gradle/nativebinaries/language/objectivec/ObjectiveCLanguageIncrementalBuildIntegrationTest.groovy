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

import org.gradle.internal.hash.HashUtil
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.language.cpp.AbstractLanguageIncrementalBuildIntegrationTest
import org.gradle.nativebinaries.language.cpp.fixtures.app.IncrementalHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.ObjectiveCHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Assume

@Requires(TestPrecondition.NOT_WINDOWS)
class ObjectiveCLanguageIncrementalBuildIntegrationTest extends AbstractLanguageIncrementalBuildIntegrationTest{

    def setupSpec(){
        multiPlatformsAvailable = OperatingSystem.current().isMacOsX();
    }

    def setup(){
        //temporally don't run clang on linux until we figured out why the clang compiler
        // creates different objectfiles
        Assume.assumeTrue(OperatingSystem.current().isMacOsX() || toolChain.displayName != "clang")
    }

    //@Ignore("Demos a problem with clang on ubuntu creating randomly differerent object files")
    def "generates always exactly same object file"() {
        setup:
        def recordings = []
        def invocation =  10
        when:
        invocation.times{
            run "cleanCompileHelloSharedLibraryHello$sourceType", "compileHelloSharedLibraryHello$sourceType"
            def oldHash= HashUtil.sha1(objectFileFor(librarySourceFiles[0], "build/objectFiles/helloSharedLibrary/hello$sourceType")).asCompactString()

            //to ensure it's not a timestamp issue
            sleep(1000)
            run "cleanCompileHelloSharedLibraryHello$sourceType", "compileHelloSharedLibraryHello$sourceType"
            def newHash = HashUtil.sha1(objectFileFor(librarySourceFiles[0], "build/objectFiles/helloSharedLibrary/hello$sourceType")).asCompactString()
            recordings << (oldHash == newHash)
        }
        then:
        recordings.findAll{ it }.size() != 0 // not everytime the .o file differs -> not a timestamp issue
        recordings.findAll{ it }.size() == invocation
    }

    def "recompiles binary when imported header file changes"() {
        sourceFile.text = sourceFile.text.replaceFirst('#include "hello.h"', "#import \"hello.h\"")

        given:
        run "installMainExecutable"


        when:
        headerFile << """
            int unused();
"""
        run "mainExecutable"

        then:
        executedAndNotSkipped libraryCompileTask
        executedAndNotSkipped mainCompileTask


        skipped ":linkHelloSharedLibrary", ":helloSharedLibrary"
        skipped ":linkMainExecutable", ":mainExecutable"
    }

    @Override
    IncrementalHelloWorldApp getHelloWorldApp() {
        return new ObjectiveCHelloWorldApp()
    }
}
