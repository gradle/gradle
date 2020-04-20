/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.classpath

import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ClasspathBuilderTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(ClasspathBuilderTest)
    def builder = new ClasspathBuilder()

    def "creates an empty jar"() {
        def file = tmpDir.file("thing.zip")

        when:
        builder.jar(file) {}

        then:
        def zip = new ZipTestFixture(file)
        zip.hasDescendants()
    }

    def "can construct jar with entries"() {
        def file = tmpDir.file("thing.zip")

        when:
        builder.jar(file) {
            it.put("a.class", "bytes".bytes)
            it.put("dir/b.class", "bytes".bytes)
        }

        then:
        def zip = new ZipTestFixture(file)
        zip.hasDescendants("a.class", "dir/b.class")
    }
}
