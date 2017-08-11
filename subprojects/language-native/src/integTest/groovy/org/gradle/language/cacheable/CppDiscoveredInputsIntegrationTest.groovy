/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cacheable

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CppDiscoveredInputsIntegrationTest extends AbstractIntegrationSpec {

    def "preprocessing and compilation are separated"() {
        file("src/main/cpp/main.cpp") << """
            #include <iostream>
            #include "greeting.hpp"
            
            int main(int argc, char** argv) {
                std::cout << greeting << std::endl;
                return 0;
            }
        """.stripIndent()

        file("src/main/headers/greeting.hpp") << """
            #ifndef GRADLE_GUIDE_EXAMPLE_GREETING_HPP
            #define GRADLE_GUIDE_EXAMPLE_GREETING_HPP
            
            namespace {
                const char * greeting = "Hello, World";
            }
            
            #endif
        """.stripIndent()

        buildFile << """ 
            import org.gradle.language.cacheable.*
            
            task preprocess(type: PreprocessNative) {
                source = file('src/main/cpp')
                preprocessedSourcesDir = new File(buildDir, 'preprocessed')
                gccExecutable = new File("/usr/bin/clang++")
            }
            
            task compile(type: CompileNative) {
                source = preprocess.outputs
                outputDir = new File(buildDir, 'compiled')
                gccExecutable = new File("/usr/bin/clang++")
            }
        """.stripIndent()

        when:
        succeeds("compile")

        then:
        executedAndNotSkipped(":preprocess")
        executedAndNotSkipped(":compile")
        file("build/preprocessed/main.ii").exists()
        file("build/compiled/main.o").exists()
    }
}
