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

package org.gradle.java.compile.incremental

import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

class JavaCompileAvoidanceWithIncrementalCompilationIntegrationTest extends AbstractCompileAvoidanceWithIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "fails when malformed jars are on the compile classpath"() {
        buildFile << """
            apply plugin: '${language.name}'
            dependencies {
                implementation files("broken.jar")
            }
        """
        file("broken.jar").text = "this is not a jar"
        file("src/main/${language.name}/Main.${language.name}") << "public class Main {}"

        expect:
        fails(language.compileTaskName)
        errorOutput.contains("zip END header not found")
    }
}
