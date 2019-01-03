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
import spock.lang.Unroll

class CppLibraryInitIntegrationTest extends AbstractInitIntegrationSpec {

    public static final String SAMPLE_LIB_CLASS = "hello.cpp"
    public static final String SAMPLE_LIB_HEADER = "hello.h"
    public static final String SAMPLE_LIB_TEST_CLASS = "hello_test.cpp"

    @Unroll
    def "creates sample source if no source present with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'cpp-library', '--project-name', 'hello', '--dsl', scriptDsl.id)

        then:
        targetDir.file("src/main/cpp").assertHasDescendants(SAMPLE_LIB_CLASS)
        targetDir.file("src/main/public").assertHasDescendants(SAMPLE_LIB_HEADER)
        targetDir.file("src/test/cpp").assertHasDescendants(SAMPLE_LIB_TEST_CLASS)

        and:
        commonFilesGenerated(scriptDsl)

        and:
        succeeds("build")

        and:
        library("build/lib/main/debug/hello").assertExists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "creates sample source with namespace and #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'cpp-library', '--project-name', 'hello', '--package', 'my::lib', '--dsl', scriptDsl.id)

        then:
        targetDir.file("src/main/cpp").assertHasDescendants(SAMPLE_LIB_CLASS)
        targetDir.file("src/main/public").assertHasDescendants(SAMPLE_LIB_HEADER)
        targetDir.file("src/test/cpp").assertHasDescendants(SAMPLE_LIB_TEST_CLASS)

        and:
        commonFilesGenerated(scriptDsl)

        and:
        targetDir.file("src/main/public/${SAMPLE_LIB_HEADER}").text.contains("namespace my {")
        targetDir.file("src/main/public/${SAMPLE_LIB_HEADER}").text.contains("namespace lib {")
        targetDir.file("src/main/cpp/${SAMPLE_LIB_CLASS}").text.contains("my::lib::")
        targetDir.file("src/test/cpp/${SAMPLE_LIB_TEST_CLASS}").text.contains("my::lib::")

        and:
        succeeds("build")

        and:
        library("build/lib/main/debug/hello").assertExists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "source generation is skipped when cpp sources detected with #scriptDsl build scripts"() {
        setup:
        targetDir.file("src/main/cpp/hola.cpp") << """
            #include <iostream>
            #include <stdlib.h>
            #include "hola.h"
            
            std::string hola() {
                return std::string("Hola, Mundo!");
            }
        """
        targetDir.file("src/main/public/hola.h") << """
            #include <string>

            extern std::string hola();
        """
        targetDir.file("src/test/cpp/hola_test.cpp") << """
            #include "hola.h"
            #include <cassert>

            int main() {
                assert(hola().compare("Hola, Mundo!") == 0);
                return 0;
            }
        """
        when:
        run('init', '--type', 'cpp-library', '--project-name', 'hola', '--dsl', scriptDsl.id)

        then:
        targetDir.file("src/main/cpp").assertHasDescendants("hola.cpp")
        targetDir.file("src/main/public").assertHasDescendants("hola.h")
        targetDir.file("src/test/cpp").assertHasDescendants("hola_test.cpp")
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        and:
        targetDir.file("src/main/cpp/${SAMPLE_LIB_CLASS}").assertDoesNotExist()
        targetDir.file("src/main/public/${SAMPLE_LIB_HEADER}").assertDoesNotExist()
        targetDir.file("src/test/cpp/${SAMPLE_LIB_TEST_CLASS}").assertDoesNotExist()

        when:
        run("build")

        then:
        executed(":test")

        and:
        library("build/lib/main/debug/hola").assertExists()

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    SharedLibraryFixture library(String path) {
        AvailableToolChains.defaultToolChain.sharedLibrary(targetDir.file(path))
    }
}
