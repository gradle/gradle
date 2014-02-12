/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 1/17/14
 */
class JavaSourceClassTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()

    def "knows output file"() {
        expect:
        new JavaSourceClass("com/Foo.java", temp.file("dir")).outputFile == temp.file("dir/com/Foo.class")
        new JavaSourceClass("Foo.java", temp.file("dir")).outputFile == temp.file("dir/Foo.class")
    }

    def "knows class name"() {
        expect:
        new JavaSourceClass("com/Foo.java", temp.file("dir")).className == "com.Foo"
        new JavaSourceClass("Foo.java", temp.file("dir")).className == "Foo"
    }
}
