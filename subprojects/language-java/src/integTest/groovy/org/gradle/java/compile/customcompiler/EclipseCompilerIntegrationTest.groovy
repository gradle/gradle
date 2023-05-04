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

            ${mavenCentralRepository()}

            configurations.create('ecj')
            dependencies {
                ecj('org.eclipse.jdt:ecj:3.33.0')
            }
            tasks.withType(JavaCompile).configureEach {
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
        result.getError().contains("error: name clash:")

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

    def "custom file manager does not cause issue with eclipse compiler"() {
        given:
        settingsFile << "include('other-project')"
        file('other-project/build.gradle') << "plugins { id 'java-library' }"
        file("other-project/src/main/java/foo/Bar.java") << """
            package foo;
            public class Bar {}
        """

        // Same package name (bar) as class on the classpath, potential issue on Windows file system
        // See: https://github.com/eclipse-jdt/eclipse.jdt.core/issues/985
        file("src/main/java/foo/bar/Main.java") << """
            package foo;
            public class Main {}
        """
        buildFile << '''
            dependencies {
                implementation project(':other-project')
            }
            tasks.withType(JavaCompile).configureEach {
                customCompilerClasspath.from(configurations.ecj)
            }
        '''

        when:
        run ':compileJava'

        then:
        result.assertTaskExecuted(':compileJava')
    }
}
