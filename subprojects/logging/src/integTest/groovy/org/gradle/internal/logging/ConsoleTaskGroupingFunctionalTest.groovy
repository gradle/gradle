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

package org.gradle.internal.logging

import org.gradle.integtests.fixtures.AbstractConsoleFunctionalSpec

class ConsoleTaskGroupingFunctionalTest extends AbstractConsoleFunctionalSpec {

    def "compiler warnings emitted from compilation task are grouped"() {
        given:
        buildFile << """
            apply plugin: 'java'

            compileJava {
                options.compilerArgs = ['-Xlint:all']
            }
        """

        file('src/main/java/Legacy.java') << """
            @Deprecated
            public class Legacy { }
        """

        file('src/main/java/MyClass.java') << """
            public class MyClass {
                public void instantiateDeprecatedClass() {
                    new Legacy();
                }
            }
        """

        when:
        succeeds('compileJava')

        then:
        result.output.contains("""> Task :compileJava\u001B[m\u001B[0K
$testDirectory/src/main/java/MyClass.java:4: warning: [deprecation] Legacy in unnamed package has been deprecated
                    new Legacy();
                        ^
1 warning""")
    }
}
