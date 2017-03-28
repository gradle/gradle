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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.Resources
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Unroll

class JavaCompileIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    Resources resources = new Resources()

    @Issue("GRADLE-3152")
    def "can use the task without applying java-base plugin"() {
        buildFile << """
            task compile(type: JavaCompile) {
                classpath = files()
                sourceCompatibility = JavaVersion.current()
                targetCompatibility = JavaVersion.current()
                destinationDir = file("build/classes")
                source "src/main/java"
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        run("compile")

        then:
        file("build/classes/Foo.class").exists()
    }

    def "uses default platform settings when applying java plugin"() {
        buildFile << """
            apply plugin:"java"
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        run("compileJava")
        then:
        file("build/classes/main/Foo.class").exists()
    }

    def "don't implicitly compile source files from classpath"() {
        settingsFile << "include 'a', 'b'"
        buildFile << """
            subprojects {
                apply plugin: 'java'
                tasks.withType(JavaCompile) {
                    options.compilerArgs << '-Xlint:all' << '-Werror'
                }
            }
            project(':b') {
                dependencies {
                    compile project(':a')
                }
            }
"""

        file("a/src/main/resources/Foo.java") << "public class Foo {}"

        file("b/src/main/java/Bar.java") << "public class Bar extends Foo {}"

        expect:
        fails("compileJava")
        failure.assertHasDescription("Execution failed for task ':b:compileJava'.")

        // This makes sure the test above is correct AND you can get back javac's default behavior if needed
        when:
        buildFile << "project(':b').compileJava { options.sourcepath = classpath }"
        run("compileJava")
        then:
        file("b/build/classes/main/Bar.class").exists()
        file("b/build/classes/main/Foo.class").exists()
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3508")
    def "detects change in classpath order"() {
        jarWithClasses(file("lib1.jar"), Thing: "class Thing {}")
        jarWithClasses(file("lib2.jar"), Thing2: "class Thing2 {}")
        file("src/main/java/Foo.java") << "public class Foo {}"

        buildFile << buildScriptWithClasspath("lib1.jar", "lib2.jar")

        when:
        run "compile"
        then:
        nonSkippedTasks.contains ":compile"

        when:
        run "compile"
        then:
        skippedTasks.contains ":compile"

        when:
        buildFile.text = buildScriptWithClasspath("lib2.jar", "lib1.jar")
        run "compile"
        then:
        nonSkippedTasks.contains ":compile"
    }

    def "stays up-to-date after file renamed on classpath"() {
        jarWithClasses(file("lib1.jar"), Thing: "class Thing {}")
        jarWithClasses(file("lib2.jar"), Thing2: "class Thing2 {}")
        file("src/main/java/Foo.java") << "public class Foo {}"

        buildFile << buildScriptWithClasspath("lib1.jar", "lib2.jar")

        when:
        run "compile"
        then:
        nonSkippedTasks.contains ":compile"

        when:
        run "compile"
        then:
        skippedTasks.contains ":compile"

        when:
        file("lib1.jar").renameTo(file("lib1-renamed.jar"))
        buildFile.text = buildScriptWithClasspath("lib1-renamed.jar", "lib2.jar")

        run "compile"
        then:
        skippedTasks.contains ":compile"
    }

    def buildScriptWithClasspath(String... dependencies) {
        """
            task compile(type: JavaCompile) {
                sourceCompatibility = JavaVersion.current()
                targetCompatibility = JavaVersion.current()
                destinationDir = file("build/classes")
                source "src/main/java"
                classpath = files('${dependencies.join("', '")}')
            }
        """
    }

    @Ignore
    def "can compile after package case-rename"() {
        buildFile << """
            apply plugin: "java"

            repositories {
                mavenCentral()
            }

            dependencies {
                testCompile "junit:junit:4.12"
            }
        """

        file("src/main/java/com/example/Foo.java") << """
            package com.example;

            public class Foo {}
        """
        file("src/test/java/com/example/FooTest.java") << """
            package com.example;

            import org.junit.Test;

            public class FooTest {
                @Test
                public void test() {
                    new com.example.Foo();
                }
            }
        """

        when:
        succeeds "test"
        then:
        nonSkippedTasks.contains ":test"
        file("build/classes/main/com/example/Foo.class").file

        when:
        // Move source file to case-renamed package
        file("src/main/java/com/example").deleteDir()
        file("src/main/java/com/Example/Foo.java") << """
            package com.Example;

            public class Foo {}
        """
        file("src/test/java/com/example/FooTest.java").text = """
            package com.example;

            import org.junit.Test;

            public class FooTest {
                @Test
                public void test() {
                    new com.Example.Foo();
                }
            }
        """

        succeeds "test"
        then:
        nonSkippedTasks.contains ":test"
        file("build/classes/main/com/Example/Foo.class").file
    }

    def "implementation dependencies should not leak into compile classpath of consuner"() {
        def shared10 = mavenRepo.module('org.gradle.test', 'shared', '1.0').publish()
        def other10 = mavenRepo.module('org.gradle.test', 'other', '1.0').publish()

        given:
        settingsFile << "include 'a', 'b'"
        buildFile << """
        allprojects {
            apply plugin: 'java'

            repositories {
               maven { url '$mavenRepo.uri' }
            }
        }

        task checkClasspath {
            doLast {
                def compileClasspath = project(':a').compileJava.classpath.files*.name
                assert compileClasspath.contains('b.jar')
                assert compileClasspath.contains('other-1.0.jar')
                assert !compileClasspath.contains('shared-1.0.jar')
            }
        }
        """

        file('a/build.gradle') << '''
            dependencies {
                implementation project(':b')
            }
        '''
        file('b/build.gradle') << '''
            dependencies {
                compile 'org.gradle.test:other:1.0' // using the old 'compile' makes it leak into compile classpath
                implementation 'org.gradle.test:shared:1.0' // but not using 'implementation'
            }
        '''

        when:
        run 'checkClasspath'

        then:
        noExceptionThrown()
    }

    def "test runtime classpath includes implementation dependencies"() {
        given:
        buildFile << '''
            apply plugin: 'java'

            repositories {
                jcenter()
            }

            dependencies {
                implementation 'org.apache.commons:commons-lang3:3.4'
                testCompile 'junit:junit:4.12' // not using testImplementation intentionaly, that's not what we want to test
            }
        '''
        file('src/main/java/Text.java') << '''import org.apache.commons.lang3.StringUtils;
            public class Text {
                public static String sayHello(String name) { return "Hello, " + StringUtils.capitalize(name); }
            }
        '''
        file('src/test/java/TextTest.java') << '''
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class TextTest {
                @Test
                public void testGreeting() {
                    assertEquals("Hello, Cedric", Text.sayHello("cedric"));
                }
            }
        '''

        when:
        run 'test'

        then:
        noExceptionThrown()
    }

    def "test runtime classpath includes test implementation dependencies"() {
        given:
        buildFile << '''
            apply plugin: 'java'

            repositories {
                jcenter()
            }

            dependencies {
                implementation 'org.apache.commons:commons-lang3:3.4'
                testImplementation 'junit:junit:4.12'
            }
        '''
        file('src/main/java/Text.java') << '''import org.apache.commons.lang3.StringUtils;
            public class Text {
                public static String sayHello(String name) { return "Hello, " + StringUtils.capitalize(name); }
            }
        '''
        file('src/test/java/TextTest.java') << '''
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class TextTest {
                @Test
                public void testGreeting() {
                    assertEquals("Hello, Cedric", Text.sayHello("cedric"));
                }
            }
        '''

        when:
        run 'test'

        then:
        noExceptionThrown()
    }

    def "test compile classpath includes implementation dependencies"() {
        given:
        buildFile << '''
            apply plugin: 'java'

            repositories {
                jcenter()
            }

            dependencies {
                implementation 'org.apache.commons:commons-lang3:3.4'
                testImplementation 'junit:junit:4.12'
            }
        '''
        file('src/main/java/Text.java') << '''import org.apache.commons.lang3.StringUtils;
            public class Text {
                public static String sayHello(String name) { return "Hello, " + StringUtils.capitalize(name); }
            }
        '''
        file('src/test/java/TextTest.java') << '''import org.apache.commons.lang3.StringUtils;
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class TextTest {
                @Test
                public void testGreeting() {
                    assertEquals(StringUtils.capitalize("hello, Cedric"), Text.sayHello("cedric"));
                }
            }
        '''

        when:
        run 'test'

        then:
        noExceptionThrown()
    }

    def "test runtime classpath includes test runtime only dependencies"() {
        mavenRepo.module('org.gradle.test', 'compile', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'compileonly', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'runtimeonly', '1.0').publish()

        given:
        settingsFile << "include 'a', 'b'"
        buildFile << """
            apply plugin: 'java'

            repositories {
                maven { url '$mavenRepo.uri' }
            }

            dependencies {
                testImplementation 'org.gradle.test:compile:1.0'
                testCompileOnly 'org.gradle.test:compileonly:1.0'
                testRuntimeOnly 'org.gradle.test:runtimeonly:1.0'
            }

        task checkClasspath {
            doLast {
                def runtimeClasspath = test.classpath.files*.name
                assert runtimeClasspath.contains('compile-1.0.jar')
                assert !runtimeClasspath.contains('compileonly-1.0.jar')
                assert runtimeClasspath.contains('runtimeonly-1.0.jar')
            }
        }
        """

        when:
        run 'checkClasspath'

        then:
        noExceptionThrown()
    }

    @Unroll
    def "can depend on #scenario without building the jar"() {
        given:
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            apply plugin: 'java'
            
            dependencies {
                implementation project(':b')
            }
            
            task processDependency {
                def lazyInputs = configurations.runtimeClasspath.incoming.artifactView { 
                    attributes { it.attribute(Attribute.of('artifactType', String), JavaPlugin.${token}) }
                }.files
                inputs.files(lazyInputs)
                doLast {
                    assert lazyInputs.files.parentFile*.name == ['${expectedDirName}']
                }
            }
        """
        file('b/build.gradle') << '''
            apply plugin: 'java'
        '''
        file('b/src/main/java/Foo.java') << 'class Foo {}'
        file('b/src/main/resources/foo.txt') << 'some resource'

        when:
        run 'processDependency'

        then:
        executedAndNotSkipped ":b:$executed"
        notExecuted ":b:$notExec"

        where:
        scenario              | token                 | expectedDirName | executed           | notExec
        'class directory'     | 'CLASS_DIRECTORY'     | 'classes'       | 'compileJava'      | 'processResources'
        'resources directory' | 'RESOURCES_DIRECTORY' | 'resources'     | 'processResources' | 'compileJava'
    }

    @Issue("gradle/gradle#1347")
    def "compile classpath snapshotting ignores non-relevant elements"() {
        buildFile << '''
            apply plugin: 'java'
            
            dependencies {
               compile files('foo.txt')
            }
        '''
        file('foo.txt') << 'should not throw an error during compile classpath snapshotting'
        file('src/main/java/Hello.java') << 'public class Hello {}'

        when:
        run 'compileJava'

        then:
        noExceptionThrown()
        executedAndNotSkipped ':compileJava'

        when: "we update a non relevant file on compile classpath"
        file('foo.txt') << 'should not trigger recompilation'
        run 'compileJava'

        then:
        skipped ':compileJava'
    }

    @Issue("gradle/gradle#1358")
    @Requires(TestPrecondition.JDK8_OR_EARLIER) // Java 9 compiler throws error already: 'zip END header not found'
    def "compile classpath snapshotting should warn when jar on classpath is malformed"() {
        buildFile << '''
            apply plugin: 'java'
            
            dependencies {
               compile files('foo.jar')
            }
        '''
        file('foo.jar') << 'this is clearly not a well formed jar file'
        file('src/main/java/Hello.java') << 'public class Hello {}'

        when:
        executer.withFullDeprecationStackTraceDisabled()
        executer.expectDeprecationWarning()
        run 'compileJava'

        then:
        executedAndNotSkipped ':compileJava'
        outputContains 'Malformed jar [foo.jar] found on compile classpath'

    }

    @Issue("gradle/gradle#1581")
    @Requires(TestPrecondition.JDK8_OR_EARLIER) // Java 9 compiler throws error already: java.nio.file.InvalidPathException: Path: nul character not allowed
    def "compile classpath snapshotting should warn when jar on classpath has non-utf8 characters in filenames"() {
        buildFile << '''
            apply plugin: 'java'
            
            dependencies {
               compile files('broken-utf8.jar')
            }
        '''
        // This file has a file name which is not UTF-8.
        // See https://bugs.openjdk.java.net/browse/JDK-7062777?focusedCommentId=12254124&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-12254124.
        resources.findResource('broken-utf8.is-a-jar').copyTo(file('broken-utf8.jar'))
        file('src/main/java/Hello.java') << 'public class Hello {}'

        when:
        executer.withFullDeprecationStackTraceDisabled()
        executer.expectDeprecationWarning()
        run 'compileJava'

        then:
        executedAndNotSkipped ':compileJava'
        outputContains 'Malformed jar [broken-utf8.jar] found on classpath'

    }

    @Issue("gradle/gradle#1358")
    def "compile classpath snapshotting should warn when jar on classpath contains malformed class file"() {
        buildFile << '''
            apply plugin: 'java'
            
            task fooJar(type:Jar) {
                archiveName = 'foo.jar'
                from file('foo.class')
            }
            
            dependencies {
               compile files(fooJar.archivePath)
            }
            
            compileJava.dependsOn(fooJar)
            
            
        '''
        file('foo.class') << 'this is clearly not a well formed class file'
        file('src/main/java/Hello.java') << 'public class Hello {}'

        when:
        executer.withFullDeprecationStackTraceDisabled()
        executer.expectDeprecationWarning()
        run 'compileJava'

        then:
        executedAndNotSkipped ':fooJar', ':compileJava'
        outputContains 'Malformed class file [foo.class]'
    }

    @Issue("gradle/gradle#1358")
    def "compile classpath snapshotting should warn when class on classpath is malformed"() {
        buildFile << '''
            apply plugin: 'java'
            
            dependencies {
               compile files('classes')
            }
            
        '''
        file('classes/foo.class') << 'this is clearly not a well formed class file'
        file('src/main/java/Hello.java') << 'public class Hello {}'

        when:
        executer.withFullDeprecationStackTraceDisabled()
        executer.expectDeprecationWarning()
        run 'compileJava'

        then:
        executedAndNotSkipped ':compileJava'
        outputContains 'Malformed class file [foo.class] found on compile classpath'
    }

    @Issue("gradle/gradle#1359")
    def "compile classpath snapshotting should support unicode class names"() {
        settingsFile << 'include "b"'
        file("b/build.gradle") << '''
            apply plugin: 'java'
        '''
        file("b/src/main/java/λ.java") << 'public class λ {}'

        buildFile << '''
            apply plugin: 'java'
            
            dependencies {
               compile project(':b')
            }
        '''
        file('src/main/java/Lambda.java') << 'public class Lambda extends λ {}'

        when:
        run 'compileJava'

        then:
        noExceptionThrown()
        executedAndNotSkipped ':compileJava'
    }

}
