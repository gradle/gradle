/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.compile

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class CompilationSourceDirsTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())

    def compilationSourceDirs = new CompilationSourceDirs(["src/main/java", "src/main/java2"].collect { temp.file(it) })

    def "relativizes source paths"() {
        expect:
        compilationSourceDirs.relativize(temp.file("src/main/java/Foo.java")) == Optional.of("Foo.java")
        compilationSourceDirs.relativize(temp.file("src/main/java/org/bar/Bar.java")) == Optional.of("org/bar/Bar.java")
        compilationSourceDirs.relativize(temp.file("src/main/java2/com/Com.java")) == Optional.of("com/Com.java")
        compilationSourceDirs.relativize(temp.file("src/main/unknown/Xxx.java")) == Optional.empty()
    }
}
