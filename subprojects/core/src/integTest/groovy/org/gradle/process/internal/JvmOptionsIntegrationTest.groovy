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

package org.gradle.process.internal

import org.gradle.integtests.fixtures.AbstractPluginIntegrationTest
import org.gradle.util.Requires
import org.gradle.util.Resources
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Issue

class JvmOptionsIntegrationTest extends AbstractPluginIntegrationTest {

    @Rule
    Resources resources = new Resources()

    @Issue("https://github.com/gradle/gradle/issues/10825")
    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "build a legal command line to execute a module's Main-Class"() {
        given:
        buildFile << runModule()
        file("src/main/java/module-info.java") << 'module my.module { exports io.greeting; }'
        file("src/main/java/io/greeting/HelloWorld.java") << helloWorld()

        when:
        run("compileJava", "run")

        then:
        file("build/classes/java/main/module-info.class").exists()
        file("build/classes/java/main/io/greeting/HelloWorld.class").exists()
        noExceptionThrown()
        outputContains("Hello Gradle World!")
    }

    @Issue("https://github.com/gradle/gradle/issues/10825")
    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "build a legal command line to execute a jar's Main-Class"() {
        given:
        settingsFile << """ rootProject.name = 'myApp' """
        buildFile << runJar()
        file("src/main/java/io/greeting/HelloWorld.java") << helloWorld()

        when:
        run("compileJava", "jar", "run")

        then:
        file("build/classes/java/main/io/greeting/HelloWorld.class").exists()
        noExceptionThrown()
        outputContains("Hello Gradle World!")
    }

    def helloWorld( ){
        return '''
            package io.greeting;

            import java.util.*;
            import static java.lang.System.*;
            import static java.lang.String.*;
            import java.util.stream.*;
            import static java.util.Arrays.asList;

            public class HelloWorld {
                private final static String hello = "Hello %s World!%n";
                static public void main(String... args){
                    assert args.length == 1 : "Should have one parameter!";
                    assert hasOnlyAppArgs(args) : "When executing a JPMS module's Main-Class, JVM options located after the application's entry point are illegal";

                    for(String world : args ){
                        out.printf(hello, world);
                    }
                }

                static private boolean hasOnlyAppArgs(String... args){
                    return !asList(args).stream().anyMatch(arg -> arg.charAt(0) == '-');
                }
            }
        '''
    }

    def runModule( ){
        return '''
            plugins {
                id 'application'
            }

            run {
                doFirst {
                    args = ['Gradle']
                    jvmArgs = [
                        '-ea',
                        '--module-path', classpath.asPath,
                        '--module', 'my.module/io.greeting.HelloWorld'
                    ]
                    classpath = files()
                    println ":run Task's command line: $commandLine"
                }
            }
        '''
    }

    def runJar( ){
        return '''
            plugins {
                id 'application'
            }

            jar {
				manifest.attributes(
					'Main-Class': 'io.greeting.HelloWorld'
				)
            }

            run {
                doFirst {
                    args = ['Gradle']
                    jvmArgs = ['-ea']
                    classpath = project.files('build/libs/myApp.jar')
                    println ":run Task's command line: $commandLine"
                }
            }
        '''
    }
}
