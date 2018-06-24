/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.scala

import org.apache.commons.lang.StringUtils
import org.gradle.integtests.fixtures.jvm.JvmSourceFile
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.integtests.language.AbstractJvmLanguageIntegrationTest
import org.gradle.language.scala.fixtures.TestJointCompiledComponent
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class JointScalaLangIntegrationTest extends AbstractJvmLanguageIntegrationTest {
    TestJvmComponent app = new TestJointCompiledComponent()

    @Requires(TestPrecondition.JDK8_OR_LATER)
    def "can compile class files with Java 8 features" () {
        app.sources.add java8SpecificClassFile

        when:
        app.writeSources(file("src/myLib"))
        app.writeResources(file("src/myLib/resources"))

        and:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec)
                }
            }
        """
        and:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":processMyLibJarMyLibResources", ":compileMyLibJarMyLib${StringUtils.capitalize(app.languageName)}"

        and:
        file("build/classes/myLib/jar").assertHasDescendants(app.expectedClasses*.fullPath as String[])
    }

    def getJava8SpecificClassFile() {
        new JvmSourceFile("compile/test", "Java8Class.java", '''
package compile.test;

import java.util.function.Function;

class Java8Class {
    public static void lambdaMethod() {
        final Function<Integer, String> f = n -> Integer.toString(n);
    }
}
''')
    }
}
