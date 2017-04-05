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

package org.gradle.nativeplatform.test.googletest

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class Test extends AbstractIntegrationSpec {
    def "test"() {
        file("src/main/cpp/main.cc").text = "void fun1() {}"
        file("src/mainTest/cpp/mainTest.cc").text = "void test() {}"
        file("src/lib/cpp/lib.cc").text = "void fun2() {}"

        buildFile.text =  """
        apply plugin: "cpp"
        apply plugin: "google-test-test-suite"

        class Rules extends RuleSource {
            @Finalize
            void linkTestedComponentLibs(@Each GoogleTestTestSuiteSpec testSuite, ComponentSpecContainer components) {
                testSuite.testedComponent.binaries.each { binary ->
                    println binary.getLibs()
                }
            }
        }
        apply plugin: Rules

        model {
            components {
                lib(NativeLibrarySpec) {

                }
                main(NativeLibrarySpec) {
                    binaries.withType(NativeBinarySpec) {
                        lib library: "lib", linkage: "static"
                    }
                }
            }
            testSuites {
                mainTest(GoogleTestTestSuiteSpec) {
                    testing \$.components.main
                }
            }
        }
        """


        when:
        run "assemble"

        then:
        executedAndNotSkipped ":assemble"
    }
}
