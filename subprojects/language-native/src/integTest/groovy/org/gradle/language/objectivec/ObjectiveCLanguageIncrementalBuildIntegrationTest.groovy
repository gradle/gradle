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

package org.gradle.language.objectivec
import org.gradle.internal.hash.HashUtil
import org.gradle.language.AbstractNativeLanguageIncrementalBuildIntegrationTest
import org.gradle.nativeplatform.fixtures.NativeLanguageRequirement
import org.gradle.nativeplatform.fixtures.RequiresSupportedLanguage
import org.gradle.nativeplatform.fixtures.app.IncrementalHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ObjectiveCHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Ignore

@Requires(TestPrecondition.OBJECTIVE_C_SUPPORT)
@RequiresSupportedLanguage(NativeLanguageRequirement.OBJECTIVE_C)
class ObjectiveCLanguageIncrementalBuildIntegrationTest extends AbstractNativeLanguageIncrementalBuildIntegrationTest {

    @Override
    boolean isCanBuildForMultiplePlatforms() {
        false
    }

    @Ignore("Demos a problem with clang on ubuntu creating randomly different object files")
    def "generates always exactly same object file"() {
        setup:
        def recordings = []
        def invocation =  10
        when:
        invocation.times{
            run "cleanCompileHelloSharedLibraryHello$sourceType", "compileHelloSharedLibraryHello$sourceType"
            def oldHash= HashUtil.sha1(objectFileFor(librarySourceFiles[0], "build/objs/helloSharedLibrary/hello$sourceType")).asCompactString()

            //to ensure it's not a timestamp issue
            sleep(1000)
            run "cleanCompileHelloSharedLibraryHello$sourceType", "compileHelloSharedLibraryHello$sourceType"
            def newHash = HashUtil.sha1(objectFileFor(librarySourceFiles[0], "build/objs/helloSharedLibrary/hello$sourceType")).asCompactString()
            recordings << (oldHash == newHash)
        }
        then:
        recordings.findAll{ it }.size() != 0 // not everytime the .o file differs -> not a timestamp issue
        recordings.findAll{ it }.size() == invocation
    }

    @Override
    IncrementalHelloWorldApp getHelloWorldApp() {
        return new ObjectiveCHelloWorldApp()
    }
}
