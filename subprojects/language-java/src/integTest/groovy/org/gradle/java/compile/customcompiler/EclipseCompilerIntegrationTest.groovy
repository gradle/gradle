/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.java.compile.customcompiler

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class EclipseCompilerIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name = 'consumer'\n"
        buildFile << """
            plugins {
                id 'java-library'
            }
            group = 'org'
            version = '1.0-beta2'

            ${mavenCentralRepository()}

            configurations.create('ecj')
            dependencies {
                ecj('org.eclipse.jdt:ecj:3.33.0')
            }
            tasks.withType(JavaCompile).configureEach {
                // customCompilerClasspath.from(configurations.ecj)
                options.headerOutputDirectory.convention(null) // https://github.com/gradle/gradle/issues/12904
            }
        """
    }

    def "compiles code with specific generics handling"() {
        given:
        file("src/main/java/foo/AbstractClass.java") << """
            package foo;
            abstract class AbstractClass {}
        """
        file("src/main/java/foo/ConcreteClassTypeA.java") << """
            package foo;
            class ConcreteClassTypeA extends AbstractClass {}
        """
        file("src/main/java/foo/DoSomething.java") << """
            package foo;
            interface DoSomething<T extends AbstractClass> {
               public void doThis(final T aClass);
            }
        """
        file("src/main/java/foo/AbstractDoSomething.java") << """
            package foo;
            abstract class AbstractDoSomething<T extends AbstractClass> implements DoSomething<T> {
               public void doThis(final AbstractClass aClass) {}
            }
        """
        file("src/main/java/foo/ConcreteTypeADoSomethings.java") << """
            package foo;
            public class ConcreteTypeADoSomethings extends AbstractDoSomething<ConcreteClassTypeA> {
               public void doThis(final ConcreteClassTypeA aClass) {}
            }
        """

        when:
        runAndFail ':compileJava'

        then:
        result.assertTaskExecuted(':compileJava')
        result.getError().contains("error: name clash: class ConcreteTypeADoSomethings has two methods with the same erasure, yet neither overrides the other")

        when:
        buildFile << '''
            tasks.withType(JavaCompile).configureEach {
                customCompilerClasspath.from(configurations.ecj) // Use the ECJ Compiler, that compiles the example code without error
            }
        '''
        run ':compileJava'

        then:
        result.assertTaskExecuted(':compileJava')
    }
}
