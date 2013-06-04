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



package org.gradle.nativecode.language.cpp

import org.gradle.nativecode.language.cpp.fixtures.AbstractBinariesIntegrationSpec

class CppExePluginIntegrationTest extends AbstractBinariesIntegrationSpec {
    def "build, install and execute simple c++ application that uses conventional layout"() {
        given:
        buildFile << """
            apply plugin: "cpp-exe"
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("src/main/headers/helloworld.h") << """
            #define MESSAGE "Hello world!"
        """

        file("src/main/cpp/helloworld.cpp") << """
            #include <iostream>
            #include "helloworld.h"

            int main () {
              std::cout << MESSAGE;
              return 0;
            }
        """

        when:
        run "mainExecutable"

        then:
        def executable = executable("build/binaries/mainExecutable/test")
        executable.assertExists()
        executable.exec().out == "Hello world!"

        when:
        run "installMainExecutable"

        then:
        def installation = installation("build/install/mainExecutable")
        installation.assertInstalled()
        installation.exec().out == "Hello world!"
    }
}
