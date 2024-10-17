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
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.Resources
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Issue

import java.nio.file.Paths

import static org.gradle.api.internal.DocumentationRegistry.BASE_URL
import static org.gradle.api.internal.DocumentationRegistry.RECOMMENDATION

// TODO: Move all of these tests to AbstractJavaCompilerIntegrationSpec
// so that we can verify them for forking, in-process, and cli compilers.
class JavaCompileIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    Resources resources = new Resources()

    def "emits deprecation warning if executable specified as relative path"() {
        given:
        def executable = TextUtil.normaliseFileSeparators(Jvm.current().javacExecutable.toString())

        buildFile << """
            plugins {
                id("java-library")
            }
            tasks.withType(JavaCompile) {
                options.fork = true
                options.forkOptions.executable = new File(".").getCanonicalFile().toPath().relativize(new File("${executable}").toPath()).toString()
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        executer.expectDocumentedDeprecationWarning("Configuring a Java executable via a relative path. " +
            "This behavior has been deprecated. This will fail with an error in Gradle 9.0. " +
            "Resolving relative file paths might yield unexpected results, there is no single clear location it would make sense to resolve against. " +
            "Configure an absolute path to a Java executable instead. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#no_relative_paths_for_java_executables")
        run("compileJava")

        then:
        result.assertTaskExecuted(":compileJava")
    }

    def "task does nothing when only minimal configuration applied"() {
        buildFile << """
            // No plugins applied
            task compile(type: JavaCompile)
        """

        when:
        run("compile")

        then:
        result.assertTasksSkipped(":compile")
    }

    @Issue("GRADLE-3152")
    def "can use the task without applying java-base plugin"() {
        buildFile << """
            task compile(type: JavaCompile) {
                classpath = files()
                sourceCompatibility = JavaVersion.current()
                targetCompatibility = JavaVersion.current()
                destinationDirectory = file("build/classes")
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
            plugins {
                id("java-library")
            }
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
                    implementation project(':a')
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
        jarWithClasses(file("lib1.jar"), Thing: "class Thing {public void foo() {} }")
        jarWithClasses(file("lib2.jar"), Thing: "class Thing { public void bar() {} }")
        file("src/main/java/Foo.java") << "public class Foo extends Thing {}"

        buildFile << buildScriptWithClasspath("lib1.jar", "lib2.jar")

        when:
        run "compile"
        then:
        executedAndNotSkipped ":compile"

        when:
        run "compile"
        then:
        skipped ":compile"

        when:
        buildFile.text = buildScriptWithClasspath("lib2.jar", "lib1.jar")
        run "compile"
        then:
        executedAndNotSkipped ":compile"
    }

    def "stays up-to-date after file renamed on classpath"() {
        jarWithClasses(file("lib1.jar"), Thing: "class Thing {}")
        jarWithClasses(file("lib2.jar"), Thing2: "class Thing2 {}")
        file("src/main/java/Foo.java") << "public class Foo {}"

        buildFile << buildScriptWithClasspath("lib1.jar", "lib2.jar")

        when:
        run "compile"
        then:
        executedAndNotSkipped ":compile"

        when:
        run "compile"
        then:
        skipped ":compile"

        when:
        file("lib1.jar").renameTo(file("lib1-renamed.jar"))
        buildFile.text = buildScriptWithClasspath("lib1-renamed.jar", "lib2.jar")

        run "compile"
        then:
        skipped ":compile"
    }

    def buildScriptWithClasspath(String... dependencies) {
        """
            task compile(type: JavaCompile) {
                sourceCompatibility = JavaVersion.current()
                targetCompatibility = JavaVersion.current()
                destinationDirectory = file("build/classes")
                source "src/main/java"
                classpath = files('${dependencies.join("', '")}')
            }
        """
    }

    @Requires(UnitTestPreconditions.Linux)
    def "can compile after package case-rename"() {
        buildFile << """
            plugins {
                id("java")
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation "junit:junit:4.13"
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
        executedAndNotSkipped ":test"
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
        executedAndNotSkipped ":test"
        javaClassFile("com/Example/Foo.class").assertIsFile()
        javaClassFile("com/example/Foo.class").assertDoesNotExist()
    }

    def "implementation dependencies should not leak into compile classpath of consumer"() {
        mavenRepo.module('org.gradle.test', 'shared', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'other', '1.0').publish()

        given:
        settingsFile << "include 'a', 'b'"
        buildFile << """
            allprojects {
                apply plugin: 'java-library'

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
                def classpath = compileJava.classpath
                doLast {
                    def compileClasspath = classpath.files*.name
                    assert !compileClasspath.contains('b.jar')
                    assert compileClasspath.contains('other-1.0.jar')
                    assert !compileClasspath.contains('shared-1.0.jar')
                }
            }
        '''
        file('b/build.gradle') << '''
            dependencies {
                api 'org.gradle.test:other:1.0'
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
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.apache.commons:commons-lang3:3.4'
                testImplementation 'junit:junit:4.13'
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
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.apache.commons:commons-lang3:3.4'
                testImplementation 'junit:junit:4.13'
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
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.apache.commons:commons-lang3:3.4'
                testImplementation 'junit:junit:4.13'
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
        createDirs("a", "b")
        settingsFile << "include 'a', 'b'"
        buildFile << """
            plugins {
                id("java-library")
            }

            repositories {
                maven { url '$mavenRepo.uri' }
            }

            dependencies {
                testImplementation 'org.gradle.test:compile:1.0'
                testCompileOnly 'org.gradle.test:compileonly:1.0'
                testRuntimeOnly 'org.gradle.test:runtimeonly:1.0'
            }

        task checkClasspath {
            def runtimeClasspathFiles = test.classpath.files
            doLast {
                def runtimeClasspath = runtimeClasspathFiles*.name
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

    def "can depend on #scenario without building the jar"() {
        given:
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation project(':b')
            }

            task processDependency {
                def lazyInputs = configurations.runtimeClasspath.incoming.artifactView {
                    attributes{ attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME)) }
                    attributes{ attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.${token})) }
                }.files
                inputs.files(lazyInputs)
                doLast {
                    assert org.gradle.util.internal.CollectionUtils.single(lazyInputs.files).toPath().endsWith("${expectedDirName}")
                }
            }
        """
        file('b/build.gradle') << '''
            plugins {
                id("java-library")
            }
        '''
        file('b/src/main/java/Foo.java') << 'class Foo {}'
        file('b/src/main/resources/foo.txt') << 'some resource'

        when:
        run 'processDependency'

        then:
        executedAndNotSkipped ":b:$executed"
        notExecuted ":b:$notExec"

        where:
        scenario              | token       | expectedDirName     | executed           | notExec
        'class directory'     | 'CLASSES'   | 'classes/java/main' | 'compileJava'      | 'processResources'
        'resources directory' | 'RESOURCES' | 'resources/main'    | 'processResources' | 'compileJava'
    }

    @Issue("gradle/gradle#1347")
    def "compile classpath snapshotting ignores non-relevant elements"() {
        def buildFileWithDependencies = { String... dependencies ->
            buildFile.text = """
                plugins {
                    id("java-library")
                }

                ${mavenCentralRepository()}

                dependencies {
                    ${dependencies.collect { "implementation ${it}" }.join('\n')}
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
    @Requires(UnitTestPreconditions.Jdk8OrEarlier)
    // Java 9 compiler throws error already: 'zip END header not found'
    def "compile classpath snapshotting should warn when jar on classpath is malformed"() {
        buildFile << '''
            plugins {
                id("java-library")
            }

            dependencies {
               implementation files('foo.jar')
            }
        '''
        file('foo.jar') << 'this is clearly not a well formed jar file'
        file('src/main/java/Hello.java') << 'public class Hello {}'

        when:
        executer.withStackTraceChecksDisabled()
        run 'compileJava'

        then:
        executedAndNotSkipped ':compileJava'
        errorOutput.contains('error in opening zip file')
    }

    @Issue("gradle/gradle#1581")
    @Requires(UnitTestPreconditions.Jdk8OrEarlier)
    def "compile classpath snapshotting on Java 8 and earlier should warn when jar on classpath has non-utf8 characters in filenames"() {
        buildFile << '''
            plugins {
                id("java-library")
            }

            dependencies {
               implementation files('broken-utf8.jar')
            }
        '''
        // This file has a file name which is not UTF-8.
        // See https://bugs.openjdk.java.net/browse/JDK-7062777?focusedCommentId=12254124&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-12254124.
        resources.findResource('broken-utf8.is-a-jar').copyTo(file('broken-utf8.jar'))
        file('src/main/java/Hello.java') << 'public class Hello {}'
        executer.withStackTraceChecksDisabled()

        when:
        run 'compileJava', '--debug'

        then:
        executedAndNotSkipped ':compileJava'
        outputContains "Malformed archive 'broken-utf8.jar'"
    }

    @Issue("gradle/gradle#1358")
    def "compile classpath snapshotting should warn when jar on classpath contains malformed class file"() {
        buildFile << '''
            plugins {
                id("java-library")
            }

            task fooJar(type:Jar) {
                archiveFileName = 'foo.jar'
                from file('foo.class')
            }

            dependencies {
               implementation files(fooJar.archiveFile)
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
        outputContains "Could not analyze foo.class for incremental compilation"
        outputContains "Unsupported class file major version"
    }

    @Issue("gradle/gradle#1358")
    def "compile classpath snapshotting should warn when class on classpath is malformed"() {
        buildFile << '''
            plugins {
                id("java-library")
            }

            dependencies {
               implementation files('classes')
            }

        '''
        file('classes/foo.class') << 'this is clearly not a well formed class file'
        file('src/main/java/Hello.java') << 'public class Hello {}'
        executer.withStackTraceChecksDisabled()

        when:
        run 'compileJava', '--debug'

        then:
        executedAndNotSkipped ':compileJava'
        outputContains "Malformed class file 'foo.class' found on compile classpath"
    }

    @Issue("gradle/gradle#1359")
    def "compile classpath snapshotting should support unicode class names"() {
        settingsFile << 'include "b"'
        file("b/build.gradle") << '''
            plugins {
                id("java-library")
            }
        '''
        file("b/src/main/java/λ.java") << 'public class λ {}'

        buildFile << '''
            plugins {
                id("java-library")
            }

            dependencies {
               implementation project(':b')
            }
        '''
        file('src/main/java/Lambda.java') << 'public class Lambda extends λ {}'

        when:
        run 'compileJava'

        then:
        noExceptionThrown()
        executedAndNotSkipped ':compileJava'
    }

    @Issue("https://github.com/gradle/gradle/issues/2463")
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
        run('compileJava')
        then:
        skipped(':compileJava')
    }

    def "ignores empty directories within the file collection of the sourcepath compiler option"() {
        given:
        def sourcePath = 'src/main/ignoredJava'
        buildFile << """
            plugins { id 'java' }
            compileJava.options.sourcepath = files('$sourcePath')
        """

        file("${sourcePath}/org/gradle/test/MyOptionalTest.java").text = """
            package org.gradle.test;

            class MyOptionalTest {}
        """

        file('src/main/java/org/gradle/test/MyTest.java').text = """
            package org.gradle.test;

            class MyTest {}
        """

        when:
        run('compileJava')
        then:
        executedAndNotSkipped(':compileJava')

        when:
        file("${sourcePath}/empty").createDir()
        run('compileJava')
        then:
        skipped(':compileJava')
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "compile a module"() {
        given:
        buildFile << '''
            plugins {
                id 'java'
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
    @Requires(UnitTestPreconditions.Jdk9OrLater)
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
    @Requires(UnitTestPreconditions.Jdk9OrLater)
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
            plugins {
                id("java-library")
            }

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
            plugins {
                id("java-library")
            }

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
            plugins {
                id("java-library")
            }

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

    @Requires([UnitTestPreconditions.Jdk8OrEarlier, IntegTestPreconditions.Java7HomeAvailable, IntegTestPreconditions.Java8HomeAvailable ])
    // bootclasspath has been removed in Java 9+
    def "bootclasspath can be set"() {
        def jdk7 = AvailableJavaHomes.getJdk7()
        def jdk7bootClasspath = TextUtil.escapeString(jdk7.jre.absolutePath) + "/lib/rt.jar"
        def jdk8 = AvailableJavaHomes.getJdk8()
        def jdk8bootClasspath = TextUtil.escapeString(jdk8.jre.absolutePath) + "/lib/rt.jar"
        buildFile << """
            plugins {
                id("java-library")
            }

            compileJava {
                if (providers.gradleProperty("java7").isPresent()) {
                    options.bootstrapClasspath = files("$jdk7bootClasspath")
                } else if (providers.gradleProperty("java8").isPresent()) {
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

        fails "-Pjava7", "clean", "compileJava"
        failure.assertHasErrorOutput "Main.java:8: error: cannot find symbol"

        succeeds "-Pjava8", "clean", "compileJava"
    }

    // bootclasspath has been removed in Java 9+
    @Requires(IntegTestPreconditions.BestJreAvailable)
    @Issue("https://github.com/gradle/gradle/issues/19817")
    def "fails if bootclasspath is provided as a path instead of a single file"() {
        def rtJar = new File(AvailableJavaHomes.bestJre, "lib/rt.jar")
        def bootClasspath = TextUtil.escapeString(rtJar.absolutePath) + "${File.pathSeparator}someotherpath"
        buildFile << """
            plugins {
                id 'java'
            }
            tasks.withType(JavaCompile) {
                options.bootstrapClasspath = project.layout.files("$bootClasspath")
            }
        """
        file('src/main/java/Foo.java') << 'public class Foo {}'

        when:
        runAndFail "compileJava"
        then:
        failure.assertHasDocumentedCause("Converting files to a classpath string when their paths contain the path separator '${File.pathSeparator}' is not supported." +
            " The path separator is not a valid element of a file path. Problematic paths in 'task ':compileJava' property 'options.bootstrapClasspath'' are: '${Paths.get(bootClasspath)}'." +
            " Add the individual files to the file collection instead." +
            " Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#file_collection_to_classpath")
    }

    def "deletes empty packages dirs"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }
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
        !file("build/classes/java/main/com/foo").exists()
    }

    def "can configure custom header output"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }
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

    def "can connect generated headers to input of another task"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            task copy(type: Copy) {
                from tasks.compileJava.options.headerOutputDirectory
                into 'headers'
            }
        """
        file('src/main/java/Foo.java') << """
            public class Foo {
                public native void foo();
            }
        """
        when:
        succeeds "copy"
        executed ":compileJava"

        then:
        file('headers').assertHasDescendants("Foo.h")
    }

    def "deletes stale header files"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }
        """
        def header = file('src/main/java/my/org/Foo.java') << """
            package my.org;

            public class Foo {
                public native void foo();
            }
        """
        def generatedHeader = file("build/generated/sources/headers/java/main/my_org_Foo.h")

        when:
        succeeds "compileJava"
        then:
        generatedHeader.isFile()

        when:
        header.delete()
        succeeds "compileJava"

        then:
        !generatedHeader.exists()
    }

    @Issue("https://github.com/gradle/gradle/issues/11017")
    def "does not use case insensitive default excludes"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }
        """
        file("src/main/java/com/example/Main.java") << """
            package com.example;

            import com.example.cvs.Test;

            public class Main {

              public static void main(String[] args) {
                System.out.println(new Test());
              }
            }
        """
        file("src/main/java/com/example/cvs/Test.java") << """
            package com.example.cvs;

            public class Test {
            }
        """

        expect:
        succeeds("compileJava")
    }

    def "CompileOptions.getAnnotationProcessorGeneratedSourcesDirectory is deprecated"() {
        when:
        buildFile << """
            plugins {
                id("java")
            }
            tasks.withType(JavaCompile) {
                doLast {
                    println(options.annotationProcessorGeneratedSourcesDirectory)
                }
            }
        """
        file("src/main/java/com/example/Main.java") << """
            package com.example;
            public class Main {}
        """
        expectAnnotationProcessorGeneratedSourcesDirectoryDeprecation()

        then:
        succeeds("compileJava")
    }

    private expectAnnotationProcessorGeneratedSourcesDirectoryDeprecation() {
        executer.expectDocumentedDeprecationWarning("The CompileOptions.annotationProcessorGeneratedSourcesDirectory property has been deprecated. " +
            "This is scheduled to be removed in Gradle 9.0. Please use the generatedSourceOutputDirectory property instead. ${getCompileOptionsLink()}")
    }

    def "CompileOptions.setAnnotationProcessorGeneratedSourcesDirectory(File) is deprecated"() {
        when:
        buildFile << """
            plugins {
                id("java")
            }
            tasks.withType(JavaCompile) {
                options.annotationProcessorGeneratedSourcesDirectory = file("build/annotation-processor-out")
            }
        """
        file("src/main/java/com/example/Main.java") << """
            package com.example;
            public class Main {}
        """
        expectAnnotationProcessorGeneratedSourcesDirectoryDeprecation()

        then:
        succeeds("compileJava")
    }

    def "CompileOptions.setAnnotationProcessorGeneratedSourcesDirectory(Provider<File>) is deprecated"() {
        when:
        buildFile << """
            plugins {
                id("java")
            }
            tasks.withType(JavaCompile) {
                options.annotationProcessorGeneratedSourcesDirectory = provider(() -> file("build/annotation-processor-out"))
            }
        """
        file("src/main/java/com/example/Main.java") << """
            package com.example;
            public class Main {}
        """
        expectAnnotationProcessorGeneratedSourcesDirectoryDeprecation()

        then:
        succeeds("compileJava")
    }

    private getCompileOptionsLink() {
        String.format(RECOMMENDATION, "information", "${BASE_URL}/dsl/org.gradle.api.tasks.compile.CompileOptions.html#org.gradle.api.tasks.compile.CompileOptions:annotationProcessorGeneratedSourcesDirectory")
    }

    @Issue("https://github.com/gradle/gradle/issues/18262")
    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "should compile sources from source with -sourcepath option for modules"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            tasks.register("compileCustomJava", JavaCompile) {
                destinationDirectory.set(new File(buildDir, "classes/java-custom-path/main"))
                source = files("src/main/java-custom-path").asFileTree
                options.sourcepath = files("src/main/java") + files("src/main/java-custom-path")
                classpath = files()
            }
        """
        file("src/main/java/com/example/SourcePathTest.java") << """
            package com.example;

            public class SourcePathTest {}
        """
        file("src/main/java-custom-path/module-info.java") << """
            module com.example {
                exports com.example;
            }
        """

        expect:
        succeeds("compileCustomJava")
        file("build/classes/java-custom-path/main/module-info.class").exists()
        // We compile only classes defined in `source`
        !file("build/classes/java-custom-path/main/com/example/SourcePathTest.class").exists()
    }

    def "Map-accepting methods are deprecated"() {
        buildFile << """
            plugins {
                id("java-library")
            }

            tasks.compileJava {
                options.define(encoding: 'UTF-8')
                options.fork(memoryMaximumSize: '1G')
                options.debug(debugLevel: 'lines')
                options.forkOptions.define([:])
                options.debugOptions.define([:])

                // Ensure replacement compiles successfully
                options.encoding = 'UTF-8'
                options.fork = true
                options.forkOptions.memoryMaximumSize = '1G'
                options.debug = true
                options.debugOptions.debugLevel = 'lines'
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The AbstractOptions.define(Map) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_abstract_options")
        executer.expectDocumentedDeprecationWarning("The CompileOptions.fork(Map) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Set properties directly on the 'forkOptions' property instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_abstract_options")
        executer.expectDocumentedDeprecationWarning("The CompileOptions.debug(Map) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Set properties directly on the 'debugOptions' property instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_abstract_options")
        executer.expectDocumentedDeprecationWarning("The AbstractOptions.define(Map) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_abstract_options")
        executer.expectDocumentedDeprecationWarning("The AbstractOptions.define(Map) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_abstract_options")
        succeeds("compileJava")
    }
}
