/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.buildinit.plugins

import org.gradle.buildinit.plugins.fixtures.ScriptDslFixture
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.SharedLibraryFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@Requires(UnitTestPreconditions.NotMacOs)
class CppLibraryInitIntegrationTest extends AbstractInitIntegrationSpec {

    public static final String SAMPLE_LIB_CLASS = "hello.cpp"
    public static final String SAMPLE_LIB_TEST_CLASS = "hello_test.cpp"

    @Override
    String subprojectName() { 'lib' }

    def "creates sample source if no source present with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'cpp-library', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/cpp").assertHasDescendants(SAMPLE_LIB_CLASS)
        subprojectDir.file("src/main/public").assertHasDescendants("some-thing.h")
        subprojectDir.file("src/test/cpp").assertHasDescendants(SAMPLE_LIB_TEST_CLASS)

        and:
        subprojectDir.file("src/main/public/some-thing.h").text.contains("namespace some_thing {")
        subprojectDir.file("src/main/public/some-thing.h").text.contains("#define SOME_THING_EXPORT_FUNC")
        subprojectDir.file("src/main/cpp/${SAMPLE_LIB_CLASS}").text.contains("some_thing::")
        subprojectDir.file("src/test/cpp/${SAMPLE_LIB_TEST_CLASS}").text.contains("some_thing::")


        and:
        commonFilesGenerated(scriptDsl)

        and:
        succeeds("build")

        and:
        library("${subprojectName()}/build/lib/main/debug/lib").assertExists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    def "creates sample source if project name is specified with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'cpp-library', '--project-name', 'greeting', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/cpp").assertHasDescendants(SAMPLE_LIB_CLASS)
        subprojectDir.file("src/main/public").assertHasDescendants("greeting.h")
        subprojectDir.file("src/test/cpp").assertHasDescendants(SAMPLE_LIB_TEST_CLASS)

        and:
        subprojectDir.file("src/main/public/greeting.h").text.contains("namespace greeting {")
        subprojectDir.file("src/main/public/greeting.h").text.contains("#define GREETING_EXPORT_FUNC")
        subprojectDir.file("src/main/cpp/${SAMPLE_LIB_CLASS}").text.contains("greeting::")
        subprojectDir.file("src/test/cpp/${SAMPLE_LIB_TEST_CLASS}").text.contains("greeting::")


        and:
        commonFilesGenerated(scriptDsl)

        and:
        succeeds("build")

        and:
        library("${subprojectName()}/build/lib/main/debug/lib").assertExists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }


    def "source generation is skipped when cpp sources detected with #scriptDsl build scripts"() {
        setup:
        subprojectDir.file("src/main/cpp/hola.cpp") << """
            #include <iostream>
            #include <stdlib.h>
            #include "hola.h"

            std::string hola() {
                return std::string("Hola, Mundo!");
            }
        """
        subprojectDir.file("src/main/public/hola.h") << """
            #include <string>

            #ifdef _WIN32
            #define EXPORT_FUNC __declspec(dllexport)
            #else
            #define EXPORT_FUNC
            #endif

            extern std::string EXPORT_FUNC hola();
        """
        subprojectDir.file("src/test/cpp/hola_test.cpp") << """
            #include "hola.h"
            #include <cassert>

            int main() {
                assert(hola().compare("Hola, Mundo!") == 0);
                return 0;
            }
        """
        when:
        run('init', '--type', 'cpp-library', '--project-name', 'hello', '--dsl', scriptDsl.id, '--overwrite')

        then:
        subprojectDir.file("src/main/cpp").assertHasDescendants("hola.cpp")
        subprojectDir.file("src/main/public").assertHasDescendants("hola.h")
        subprojectDir.file("src/test/cpp").assertHasDescendants("hola_test.cpp")
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        and:
        subprojectDir.file("src/main/cpp/${SAMPLE_LIB_CLASS}").assertDoesNotExist()
        subprojectDir.file("src/main/public/hello.h").assertDoesNotExist()
        subprojectDir.file("src/test/cpp/${SAMPLE_LIB_TEST_CLASS}").assertDoesNotExist()

        when:
        run("build")

        then:
        executed(":lib:test")

        and:
        library("${subprojectName()}/build/lib/main/debug/lib").assertExists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    SharedLibraryFixture library(String path) {
        AvailableToolChains.defaultToolChain.sharedLibrary(targetDir.file(path))
    }
}
