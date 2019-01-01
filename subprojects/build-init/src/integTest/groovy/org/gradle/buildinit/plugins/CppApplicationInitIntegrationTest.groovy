/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.nativeplatform.fixtures.ExecutableFixture
import org.gradle.util.TextUtil
import spock.lang.Unroll

class CppApplicationInitIntegrationTest extends AbstractInitIntegrationSpec {

    public static final String SAMPLE_APP_CLASS = "app.cpp"
    public static final String SAMPLE_APP_HEADER = "app.h"
    public static final String SAMPLE_APP_TEST_CLASS = "app_test.cpp"

    @Unroll
    def "creates sample source if no source present with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'cpp-application', '--project-name', 'app', '--dsl', scriptDsl.id)

        then:
        targetDir.file("src/main/cpp").assertHasDescendants(SAMPLE_APP_CLASS)
        targetDir.file("src/main/headers").assertHasDescendants(SAMPLE_APP_HEADER)
        targetDir.file("src/test/cpp").assertHasDescendants(SAMPLE_APP_TEST_CLASS)

        and:
        commonFilesGenerated(scriptDsl)

        and:
        succeeds("build")

        and:
        executable("build/install/main/debug/app").exec().out ==  TextUtil.toPlatformLineSeparators("Hello, World!\n")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "creates sample source with namespace and #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'cpp-application', '--project-name', 'app', '--package', 'my::app', '--dsl', scriptDsl.id)

        then:
        targetDir.file("src/main/cpp").assertHasDescendants(SAMPLE_APP_CLASS)
        targetDir.file("src/main/headers").assertHasDescendants(SAMPLE_APP_HEADER)
        targetDir.file("src/test/cpp").assertHasDescendants(SAMPLE_APP_TEST_CLASS)

        and:
        commonFilesGenerated(scriptDsl)

        and:
        targetDir.file("src/main/headers/${SAMPLE_APP_HEADER}").text.contains("namespace my::app")

        and:
        succeeds("build")

        and:
        executable("build/install/main/debug/app").exec().out ==  TextUtil.toPlatformLineSeparators("Hello, World!\n")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    @Unroll
    def "source generation is skipped when cpp sources detected with #scriptDsl build scripts"() {
        setup:
        targetDir.file("src/main/cpp/hello.cpp") << """
            #include <iostream>
            #include <stdlib.h>
            #include "hello.h"
            
            std::string hello() {
                return std::string("Hola, Mundo!");
            }
            
            int main () {
                std::cout << hello() << std::endl;
                return 0;
            }
        """
        targetDir.file("src/main/headers/hello.h") << """
            #include <string>

            extern std::string hello();
        """
        targetDir.file("src/test/cpp/hello_test.cpp") << """
            #include "hello.h"
            #include <cassert>

            int main() {
                assert(hello().compare("Hola, Mundo!") == 0);
                return 0;
            }
        """
        when:
        run('init', '--type', 'cpp-application', '--project-name', 'app', '--dsl', scriptDsl.id)

        then:
        targetDir.file("src/main/cpp").assertHasDescendants("hello.cpp")
        targetDir.file("src/main/headers").assertHasDescendants("hello.h")
        targetDir.file("src/test/cpp").assertHasDescendants("hello_test.cpp")
        dslFixtureFor(scriptDsl).assertGradleFilesGenerated()

        and:
        targetDir.file("src/main/cpp/${SAMPLE_APP_CLASS}").assertDoesNotExist()
        targetDir.file("src/main/headers/${SAMPLE_APP_HEADER}").assertDoesNotExist()
        targetDir.file("src/test/cpp/${SAMPLE_APP_TEST_CLASS}").assertDoesNotExist()

        when:
        run("build")

        then:
        executed(":test")

        and:
        executable("build/install/main/debug/app").exec().out ==  TextUtil.toPlatformLineSeparators("Hola, Mundo!\n")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }

    ExecutableFixture executable(String path) {
        AvailableToolChains.defaultToolChain.executable(targetDir.file(path))
    }
}
