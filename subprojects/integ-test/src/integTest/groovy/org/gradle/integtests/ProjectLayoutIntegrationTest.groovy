/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.ZincScalaCompileFixture
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProjectLayoutIntegrationTest extends AbstractIntegrationTest {

    @Rule
    public final ZincScalaCompileFixture zincScalaCompileFixture = new ZincScalaCompileFixture(executer, testDirectoryProvider)

    @Rule
    public final TestResources resources = new TestResources(testDirectoryProvider)

    @Before
    void setUp() {
        executer.withRepositoryMirrors()
    }

    @Test
    void canHaveSomeSourceAndResourcesInSameDirectoryAndSomeInDifferentDirectories() {
        file('settings.gradle') << 'rootProject.name = "sharedSource"'
        file('build.gradle') << """
apply plugin: 'java'
apply plugin: 'groovy'
apply plugin: 'scala'

${mavenCentralRepository()}
dependencies {
    implementation 'org.codehaus.groovy:groovy-all:2.4.10'
    implementation 'org.scala-lang:scala-library:2.11.12'
}

testing.suites.test.useJUnitJupiter()

sourceSets.each {
    configure(it) {
        resources.srcDir 'src'
        resources.srcDir 'src/resources'
        resources.include "org/gradle/\$name/**"
        java.srcDir 'src'
        java.srcDir 'src/java'
        java.include "org/gradle/\$name/**/*.java"
        groovy.srcDir 'src'
        groovy.srcDir 'src/groovy'
        groovy.include "org/gradle/\$name/**/*.groovy"
        scala.srcDir 'src'
        scala.srcDir 'src/scala'
        scala.include "org/gradle/\$name/**/*.scala"
    }
}
"""
        file('src/org/gradle/main/resource.txt') << 'some text'
        file('src/org/gradle/test/resource.txt') << 'some text'
        file('src/resources/org/gradle/main/resource2.txt') << 'some text'
        file('src/resources/org/gradle/test/resource2.txt') << 'some text'
        file('src/org/gradle/main/JavaClass.java') << 'package org.gradle; public class JavaClass { }'
        file('src/org/gradle/test/JavaClassTest.java') << 'package org.gradle; class JavaClassTest { JavaClass c = new JavaClass(); @org.junit.jupiter.api.Test public void test() { } }'
        file('src/java/org/gradle/main/JavaClass2.java') << 'package org.gradle; class JavaClass2 { }'
        file('src/java/org/gradle/test/JavaClassTest2.java') << 'package org.gradle; class JavaClassTest2 { JavaClass c = new JavaClass(); @org.junit.jupiter.api.Test public void test() { } }'
        file('src/org/gradle/main/GroovyClass.groovy') << 'package org.gradle; class GroovyClass { }'
        file('src/org/gradle/test/GroovyClassTest.groovy') << 'package org.gradle; class GroovyClassTest { GroovyClass c = new GroovyClass(); @org.junit.jupiter.api.Test void test() { } }'
        file('src/groovy/org/gradle/main/GroovyClass2.groovy') << 'package org.gradle; class GroovyClass2 { }'
        file('src/groovy/org/gradle/test/GroovyClassTest2.groovy') << 'package org.gradle; class GroovyClassTest2 { GroovyClass c = new GroovyClass(); @org.junit.jupiter.api.Test public void test() { } }'
        file('src/org/gradle/main/ScalaClass.scala') << 'package org.gradle; class ScalaClass { }'
        file('src/org/gradle/test/ScalaClassTest.scala') << 'package org.gradle; class ScalaClassTest { val c: ScalaClass = new ScalaClass(); @org.junit.jupiter.api.Test def test { } }'
        file('src/scala/org/gradle/main/ScalaClass2.scala') << 'package org.gradle; class ScalaClass2 { }'
        file('src/scala/org/gradle/test/ScalaClassTest2.scala') << 'package org.gradle; class ScalaClassTest2 { val c: ScalaClass = new ScalaClass(); @org.junit.jupiter.api.Test def test { } }'

        executer.withTasks('build').run()

        File buildDir = file('build')

        buildDir.file('classes/java/main').assertHasDescendants(
            'org/gradle/JavaClass.class',
            'org/gradle/JavaClass2.class'
        )
        buildDir.file('classes/groovy/main').assertHasDescendants(
            'org/gradle/GroovyClass.class',
            'org/gradle/GroovyClass2.class',
        )
        buildDir.file('classes/scala/main').assertHasDescendants(
            'org/gradle/ScalaClass.class',
            'org/gradle/ScalaClass2.class'
        )

        buildDir.file('resources/main').assertHasDescendants(
                'org/gradle/main/resource.txt',
                'org/gradle/main/resource2.txt'
        )

        buildDir.file('classes/java/test').assertHasDescendants(
                'org/gradle/JavaClassTest.class',
                'org/gradle/JavaClassTest2.class',
        )
        buildDir.file('classes/groovy/test').assertHasDescendants(
            'org/gradle/GroovyClassTest.class',
            'org/gradle/GroovyClassTest2.class',
        )
        buildDir.file('classes/scala/test').assertHasDescendants(
            'org/gradle/ScalaClassTest.class',
            'org/gradle/ScalaClassTest2.class'
        )

        buildDir.file('resources/test').assertHasDescendants(
                'org/gradle/test/resource.txt',
                'org/gradle/test/resource2.txt',
        )

        TestFile tmpDir = file('jarContents')
        buildDir.file('libs/sharedSource.jar').unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/main/resource.txt',
                'org/gradle/main/resource2.txt',
                'org/gradle/JavaClass.class',
                'org/gradle/JavaClass2.class',
                'org/gradle/GroovyClass.class',
                'org/gradle/GroovyClass2.class',
                'org/gradle/ScalaClass.class',
                'org/gradle/ScalaClass2.class'
        )

        def runScalaDoc = !GradleContextualExecuter.daemon
        def tasks = ['javadoc', 'groovydoc']
        if (runScalaDoc) {
            tasks << "scaladoc"
        }

        executer.withTasks(tasks).run()

        buildDir.file('docs/javadoc/index.html').assertIsFile()
        buildDir.file('docs/groovydoc/index.html').assertIsFile()

        if (runScalaDoc) {
            buildDir.file('docs/scaladoc/index.html').assertIsFile()
        }
    }

    @Test
    void multipleProjectsCanShareTheSameSourceDirectory() {
        file('settings.gradle') << 'include "a", "b"'
        file('a/build.gradle') << '''
apply plugin: 'java'
sourceSets.main.java {
    srcDirs '../src'
    include 'org/gradle/a/**'
}
'''
        file('b/build.gradle') << '''
apply plugin: 'java'
dependencies { implementation project(':a') }
sourceSets.main.java {
    srcDirs '../src'
    include 'org/gradle/b/**'
}
'''

        file('src/org/gradle/a/ClassA.java') << 'package org.gradle.a; public class ClassA { }'
        file('src/org/gradle/b/ClassB.java') << 'package org.gradle.b; public class ClassB { private org.gradle.a.ClassA field; }'

        executer.withTasks('clean', 'assemble').run()

        file('a/build/classes/java/main').assertHasDescendants(
                'org/gradle/a/ClassA.class'
        )
        file('b/build/classes/java/main').assertHasDescendants(
                'org/gradle/b/ClassB.class'
        )
    }

    @Test
    void canUseANonStandardBuildDir() {
        executer.withTasks('build').run()

        file('build').assertDoesNotExist()

        def results = new DefaultTestExecutionResult(file(), 'target')
        results.assertTestClassesExecuted('PersonTest')
        results.testClass('PersonTest').assertTestsExecuted('ok')
    }

    @Test
    void projectPathsResolvedRelativeToRoot() {
        file('relative/a/build.gradle') << """
            task someTask
        """
        file('settings.gradle') << '''
        include ':a'
        project(':a').projectDir = new File('relative/a')
        '''
        executer.inDirectory(file('relative/a')).withTasks(':a:someTask').run()
    }
}
