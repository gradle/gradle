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
package org.gradle.nativebinaries.language.cpp

import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import spock.lang.Ignore

@Ignore // Test demonstrates missing functionality in incremental build with C++
class HeaderFileIncrementalBuildIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def "recompiles binary when header file with relative path changes"() {
        when:
        buildFile << """
            apply plugin: 'cpp'
            executables {
                main {}
            }
"""

        file("src/main/cpp/main.cpp") << """
            #include "../not_included/hello.h"

            int main () {
              sayHello();
              return 0;
            }
"""

        def headerFile = file("src/main/not_included/hello.h") << """
            void sayHello();
"""

        file("src/main/cpp/hello.cpp") << """
            #include <iostream>

            void sayHello() {
                std::cout << "HELLO" << std::endl;
            }
"""
        then:
        succeeds "mainExecutable"
        executable("build/binaries/mainExecutable/main").exec().out == "HELLO\n"

        when:
        headerFile.text = """
            NOT A VALID HEADER FILE
"""
        then:
        fails "mainExecutable"
        and:
        executedAndNotSkipped "compileMainExecutableMainCpp"
    }
}
