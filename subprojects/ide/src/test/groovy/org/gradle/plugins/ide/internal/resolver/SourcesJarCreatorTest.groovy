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

package org.gradle.plugins.ide.internal.resolver

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarFile

@UsesNativeServices
@CleanupTestDirectory(fieldName = "tmpDir")
class SourcesJarCreatorTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    TestFile outputJar = tmpDir.testDirectory.file('gradle-api-sources.jar')

    def "creates JAR file from given directory"() {
        given:
        def sourcesDir = tmpDir.createDir('src')
        sourcesDir.createFile("org/gradle/Test.java").writelns("package org.gradle;", "public class Test {}")
        sourcesDir.createFile("foo/bar/fizz/Buzz.java").writelns("package foo.bar.fizz;", "public class Buzz {}")

        when:
        SourcesJarCreator.create(outputJar, sourcesDir)

        then:
        TestFile[] contents = tmpDir.testDirectory.listFiles().findAll { it.isFile() }
        contents.length == 1
        contents[0] == outputJar

        handleAsJarFile(outputJar) { JarFile file ->
            List<JarEntry> entries = file.entries() as List
            assert entries*.name == [
                Paths.get("org/gradle/Test.java").toString(),
                Paths.get("foo/bar/fizz/Buzz.java").toString()
            ]
        }
    }

    static void handleAsJarFile(File jar, @ClosureParams(value = SimpleType, options = ["java.util.jar.JarFile"]) Closure<?> c) {
        new JarFile(jar).withCloseable(c)
    }

}

