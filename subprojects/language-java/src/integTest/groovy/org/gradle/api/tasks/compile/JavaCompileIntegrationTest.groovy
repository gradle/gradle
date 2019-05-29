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

import org.gradle.integtests.fixtures.AbstractPluginIntegrationTest
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.util.Requires
import org.gradle.util.Resources
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil
import org.gradle.util.ToBeImplemented
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Unroll

class JavaCompileIntegrationTest extends AbstractPluginIntegrationTest {

    @Rule
    Resources resources = new Resources()

    def "uses default platform settings when applying java plugin"() {
        buildFile << """
            apply plugin:"java"
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        run("compileJava")
        then:
        javaClassFile("Foo.class").exists()
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
        file("b/build/classes/java/main/Bar.class").exists()
        file("b/build/classes/java/main/Foo.class").exists()
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

            ${mavenCentralRepository()}

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
        javaClassFile("com/example/Foo.class").assertIsFile()

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
        javaClassFile("com/example/Foo.class").assertIsFile()
    }

    def "implementation dependencies should not leak into compile classpath of consumer"() {
        mavenRepo.module('org.gradle.test', 'shared', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'other', '1.0').publish()

        given:
        settingsFile << "include 'a', 'b'"
        buildFile << """
            allprojects {
                apply plugin: 'java'
    
                repositories {
                   maven { url '$mavenRepo.uri' }
                }
            }
        """

        file('a/build.gradle') << '''
            dependencies {
                implementation project(':b')
            }
            
            task checkClasspath {
                doLast {
                    def compileClasspath = compileJava.classpath.files*.name
                    assert compileClasspath.contains('b.jar')
                    assert compileClasspath.contains('other-1.0.jar')
                    assert !compileClasspath.contains('shared-1.0.jar')
                }
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
        buildFile << """
            apply plugin: 'java'

             ${jcenterRepository()}

            dependencies {
                implementation 'org.apache.commons:commons-lang3:3.4'
                testCompile 'junit:junit:4.12' // not using testImplementation intentionally, that's not what we want to test
            }
        """
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
        buildFile << """
            apply plugin: 'java'

            ${jcenterRepository()}

            dependencies {
                implementation 'org.apache.commons:commons-lang3:3.4'
                testImplementation 'junit:junit:4.12'
            }
        """
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
        buildFile << """
            apply plugin: 'java'

            ${jcenterRepository()}

            dependencies {
                implementation 'org.apache.commons:commons-lang3:3.4'
                testImplementation 'junit:junit:4.12'
            }
        """
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
                    attributes{ attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.${token})) }
                }.files
                inputs.files(lazyInputs)
                doLast {
                    assert org.gradle.util.CollectionUtils.single(lazyInputs.files).toPath().endsWith("${expectedDirName}")
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
        scenario              | token                    | expectedDirName     | executed           | notExec
        'class directory'     | 'JAVA_RUNTIME_CLASSES'   | 'classes/java/main' | 'compileJava'      | 'processResources'
        'resources directory' | 'JAVA_RUNTIME_RESOURCES' | 'resources/main'    | 'processResources' | 'compileJava'
    }

    @Issue("gradle/gradle#1347")
    def "compile classpath snapshotting ignores non-relevant elements"() {
        def buildFileWithDependencies = { String... dependencies ->
            buildFile.text = """
                apply plugin: 'java'
                                  
                ${mavenCentralRepository()}

                dependencies {
                    ${dependencies.collect { "compile ${it}"}.join('\n') }
                }
            """
        }

        def ignoredFile = file('foo.txt') << 'should not throw an error during compile classpath snapshotting'
        file('bar.txt') << 'should be ignored, too'
        file('src/main/java/Hello.java') << 'public class Hello {}'

        def nonIgnoredDependency = '"org.apache.commons:commons-lang3:3.4"'
        def ignoredDependency = 'files("foo.txt")'
        def anotherIgnoredDependency = 'files("bar.txt")'

        when:
        buildFileWithDependencies(ignoredDependency, nonIgnoredDependency)
        run 'compileJava'

        then:
        noExceptionThrown()
        executedAndNotSkipped ':compileJava'

        when: "we update a non relevant file on compile classpath"
        buildFileWithDependencies(ignoredDependency, nonIgnoredDependency)
        ignoredFile << 'should not trigger recompilation'
        run 'compileJava'

        then:
        skipped ':compileJava'

        when: "we remove a non relevant file from compile classpath"
        buildFileWithDependencies(nonIgnoredDependency)
        run 'compileJava'

        then:
        skipped ':compileJava'

        when: "we add a non-relevant element to the classpath"
        buildFileWithDependencies(ignoredDependency, anotherIgnoredDependency, nonIgnoredDependency)
        succeeds 'compileJava'

        then:
        skipped ':compileJava'

        when: "we reorder ignored elements on the classpath"
        buildFileWithDependencies(anotherIgnoredDependency, nonIgnoredDependency, ignoredDependency)
        succeeds('compileJava')

        then:
        skipped ':compileJava'

        when: "we duplicate ignored elements on the classpath"
        buildFileWithDependencies(anotherIgnoredDependency, anotherIgnoredDependency, nonIgnoredDependency, ignoredDependency)
        succeeds('compileJava')

        then:
        skipped ':compileJava'

        when: "we remove a non relevant file from disk"
        buildFileWithDependencies(ignoredDependency, nonIgnoredDependency)
        assert ignoredFile.delete()
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
        executer.withFullDeprecationStackTraceDisabled().withStackTraceChecksDisabled()
        run 'compileJava'

        then:
        executedAndNotSkipped ':compileJava'
        errorOutput.contains('error in opening zip file')
    }

    @Issue("gradle/gradle#1581")
    def "classpath snapshotting should accept non-utf8 characters in filenames"() {
        buildFile << '''
            apply plugin: 'java'
            
            dependencies {
               compile files('broken-utf8.jar')
            }
        '''
        resources.findResource('broken-utf8.is-a-jar').copyTo(file('broken-utf8.jar'))
        file('src/main/java/Hello.java') << 'public class Hello {}'

        when:
        run 'compileJava', '--debug'

        then:
        executedAndNotSkipped ':compileJava'
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
        executer.withStackTraceChecksDisabled()

        when:
        run 'compileJava', '--debug'

        then:
        executedAndNotSkipped ':fooJar', ':compileJava'
        outputContains "Malformed archive 'foo.jar'"
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
        executer.withStackTraceChecksDisabled()

        when:
        run 'compileJava', '--debug'

        then:
        executedAndNotSkipped ':compileJava'
        outputContains"Malformed class file 'foo.class' found on compile classpath"
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

    @ToBeImplemented
    @Issue(["https://github.com/gradle/gradle/issues/2463", "https://github.com/gradle/gradle/issues/3444"])
    def "non-incremental java compilation ignores empty packages"() {
        given:
        buildFile << """
            plugins { id 'java' }
            compileJava.options.incremental = false
        """

        file('src/main/java/org/gradle/test/MyTest.java').text = """
            package org.gradle.test;
            
            class MyTest {}
        """

        when:
        run 'compileJava'
        then:
        executedAndNotSkipped(':compileJava')

        when:
        file('src/main/java/org/gradle/different').createDir()
        run('compileJava', '--info')
        then:
        // FIXME: should be skipped
        executedAndNotSkipped(':compileJava')
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "compile a module"() {
        given:
        buildFile << '''
            plugins {
                id 'org.gradle.java.experimental-jigsaw' version '0.1.1'
            }
        '''
        file("src/main/java/module-info.java") << 'module example { exports io.example; }'
        file("src/main/java/io/example/Example.java") << '''
            package io.example;
            
            public class Example {}
        '''

        when:
        run "compileJava"

        then:
        noExceptionThrown()
        file("build/classes/java/main/module-info.class").exists()
        file("build/classes/java/main/io/example/Example.class").exists()
    }

    @Issue("https://github.com/gradle/gradle/issues/2537")
    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "compile a module with --module-source-path"() {
        given:
        buildFile << '''
            plugins {
                id 'java'
            }
            
            compileJava {
                options.compilerArgs = ['--module-source-path', files('src/main/java', 'src/main/moreJava').asPath]
            }
        '''
        file("src/main/java/example/module-info.java") << '''
        module example {
            exports io.example;
            requires another;
        }
        '''
        file("src/main/java/example/io/example/Example.java") << '''
            package io.example;
            
            import io.another.BaseExample;
            
            public class Example extends BaseExample {}
        '''
        file("src/main/moreJava/another/module-info.java") << 'module another { exports io.another; }'
        file("src/main/moreJava/another/io/another/BaseExample.java") << '''
            package io.another;
            
            public class BaseExample {}
        '''

        when:
        run "compileJava"

        then:
        noExceptionThrown()
        file("build/classes/java/main/example/module-info.class").exists()
        file("build/classes/java/main/example/io/example/Example.class").exists()
        file("build/classes/java/main/another/module-info.class").exists()
        file("build/classes/java/main/another/io/another/BaseExample.class").exists()
    }

    @Issue("https://github.com/gradle/gradle/issues/2537")
    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "compile a module with --module-source-path and sourcepath warns and removes sourcepath"() {
        given:
        buildFile << '''
            plugins {
                id 'java'
            }
            
            compileJava {
                options.compilerArgs = ['--module-source-path', files('src/main/java', 'src/main/moreJava').asPath]
                options.sourcepath = files('src/main/ignoredJava')
            }
        '''
        file("src/main/java/example/module-info.java") << '''
        module example {
            exports io.example;
            requires another;
        }
        '''
        file("src/main/java/example/io/example/Example.java") << '''
            package io.example;
            
            import io.another.BaseExample;
            
            public class Example extends BaseExample {}
        '''
        file("src/main/moreJava/another/module-info.java") << 'module another { exports io.another; }'
        file("src/main/moreJava/another/io/another/BaseExample.java") << '''
            package io.another;
            
            public class BaseExample {}
        '''
        file("src/main/ignoredJava/ignored/module-info.java") << 'module ignored { exports io.ignored; }'
        file("src/main/ignoredJava/ignored/io/ignored/IgnoredExample.java") << '''
            package io.ignored;
            
            public class IgnoredExample {}
        '''

        when:
        run "compileJava"

        then:
        noExceptionThrown()
        outputContains("You specified both --module-source-path and a sourcepath. These options are mutually exclusive. Ignoring sourcepath.")
        file("build/classes/java/main/example/module-info.class").exists()
        file("build/classes/java/main/example/io/example/Example.class").exists()
        file("build/classes/java/main/another/module-info.class").exists()
        file("build/classes/java/main/another/io/another/BaseExample.class").exists()
        !file("build/classes/java/main/ignored/module-info.class").exists()
        !file("build/classes/java/main/ignored/io/ignored/IgnoredExample.class").exists()
    }

    def "fails when sourcepath is set on compilerArgs"() {
        buildFile << '''
            apply plugin: 'java'
            
            compileJava {
                options.compilerArgs = ['-sourcepath', files('src/main/java').asPath]
            }
        '''
        file('src/main/java/Square.java') << 'public class Square extends Rectangle {}'

        when:
        fails 'compileJava'

        then:
        failureHasCause("Cannot specify -sourcepath or --source-path via `CompileOptions.compilerArgs`. Use the `CompileOptions.sourcepath` property instead.")
    }

    def "fails when processorpath is set on compilerArgs"() {
        buildFile << '''
            apply plugin: 'java'
            
            compileJava {
                options.compilerArgs = ['-processorpath', files('src/main/java').asPath]
            }
        '''
        file('src/main/java/Square.java') << 'public class Square extends Rectangle {}'

        when:
        fails 'compileJava'

        then:
        failureHasCause("Cannot specify -processorpath or --processor-path via `CompileOptions.compilerArgs`. Use the `CompileOptions.annotationProcessorPath` property instead.")
    }

    def "fails when a -J (compiler JVM) flag is set on compilerArgs"() {
        buildFile << '''
            apply plugin: 'java'

            compileJava {
                options.compilerArgs = ['-J-Xdiag']
            }
        '''
        file('src/main/java/Square.java') << 'public class Square extends Rectangle {}'

        when:
        fails 'compileJava'

        then:
        failureHasCause("Cannot specify -J flags via `CompileOptions.compilerArgs`. Use the `CompileOptions.forkOptions.jvmArgs` property instead.")
    }

    @Requires(adhoc = { AvailableJavaHomes.getJdk7() && AvailableJavaHomes.getJdk8() && TestPrecondition.NOT_JDK_IBM.fulfilled && TestPrecondition.FIX_TO_WORK_ON_JAVA9.fulfilled })
    def "bootclasspath can be set"() {
        def jdk7 = AvailableJavaHomes.getJdk7()
        def jdk7bootClasspath = TextUtil.escapeString(jdk7.jre.homeDir.absolutePath) + "/lib/rt.jar"
        def jdk8 = AvailableJavaHomes.getJdk8()
        def jdk8bootClasspath = TextUtil.escapeString(jdk8.jre.homeDir.absolutePath) + "/lib/rt.jar"
        buildFile << """
            apply plugin: 'java'
            
            compileJava {
                if (project.hasProperty("java7")) {
                    options.bootstrapClasspath = files("$jdk7bootClasspath")
                } else if (project.hasProperty("java8")) {
                    options.bootstrapClasspath = files("$jdk8bootClasspath")
                } 
                options.fork = true
            }
        """
        file('src/main/java/Main.java') << """
            import java.nio.file.Files;
            import java.nio.file.Paths;

            public class Main {
                public static void main(String... args) throws Exception {
                    // Use Files.lines() method introduced in Java 8
                    System.out.println("Line count: " + Files.lines(Paths.get(args[0])));
                }
            }
        """

        expect:
        succeeds "clean", "compileJava"

        executer.withStacktraceDisabled()
        fails "-Pjava7", "clean", "compileJava"
        failure.assertHasErrorOutput "Main.java:8: error: cannot find symbol"

        succeeds "-Pjava8", "clean", "compileJava"
    }

    def "deletes empty packages dirs"() {
        given:
        buildFile << """
            apply plugin: 'java'
        """
        def a = file('src/main/java/com/foo/internal/A.java') << """
            package com.foo.internal;
            public class A {}
        """
        file('src/main/java/com/bar/B.java') << """
            package com.bar;
            public class B {}
        """

        succeeds "compileJava"
        a.delete()

        when:
        succeeds "compileJava"

        then:
        ! file("build/classes/java/main/com/foo").exists()
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    def "can configure custom header output"() {
        given:
        buildFile << """
            apply plugin: 'java'
            compileJava.options.headerOutputDirectory = file("build/headers/java/main")
        """
        file('src/main/java/Foo.java') << """
            public class Foo {
                public native void foo();
            }
        """
        when:
        succeeds "compileJava"

        then:
        file("build/headers/java/main/Foo.h").exists()
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    def "deletes stale header files"() {
        given:
        buildFile << """
            apply plugin: 'java'
            compileJava.options.headerOutputDirectory = file("build/headers/java/main")
        """
        def header = file('src/main/java/Foo.java') << """
            public class Foo {
                public native void foo();
            }
        """
        succeeds "compileJava"

        when:
        header.delete()
        succeeds "compileJava"

        then:
        !file("build/headers/java/main/Foo.h").exists()
    }

    def "emits deprecation warning for effectiveAnnotationProcessorPath property"() {
        buildScript("""
            apply plugin: 'java'
                        
            ${jcenterRepository()}

            task printAnnotationProcessors {
                doLast {
                    println compileJava.effectiveAnnotationProcessorPath
                }
            }
        """.stripIndent())

        when:
        executer.expectDeprecationWarning()
        succeeds 'printAnnotationProcessors'

        then:
        outputContains('The JavaCompile.effectiveAnnotationProcessorPath property has been deprecated.')
        outputContains('Please use the JavaCompile.options.annotationProcessorPath property instead.')
    }
}
