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
import spock.lang.Subject

class InputOutputMapperTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    @Subject mapper = new InputOutputMapper([temp.file("src/main/java"), temp.file("src/main/java2")], temp.file("out"))

    def "knows java source class relative path"() {
        expect:
        mapper.toJavaSourceClass(temp.file("src/main/java/Foo.java")).relativePath == "Foo.java"
        mapper.toJavaSourceClass(temp.file("src/main/java/org/bar/Bar.java")).relativePath == "org/bar/Bar.java"
        mapper.toJavaSourceClass(temp.file("src/main/java2/com/Com.java")).relativePath == "com/Com.java"

        when: mapper.toJavaSourceClass(temp.file("src/main/unknown/Xxx.java"))
        then: thrown(IllegalArgumentException)
    }

    def "infers java source class from name"() {
        temp.createFile("src/main/java/Foo.java")
        temp.createFile("src/main/java/org/bar/Bar.java")
        temp.createFile("src/main/java2/com/Com.java")
        temp.createFile("src/main/unknown/Xxx.java")

        expect:
        mapper.toJavaSourceClass("Foo").relativePath == "Foo.java"
        mapper.toJavaSourceClass("org.bar.Bar").relativePath == "org/bar/Bar.java"
        mapper.toJavaSourceClass("com.Com").relativePath == "com/Com.java"

        when: mapper.toJavaSourceClass(temp.file("unknown.Xxx"))
        then: thrown(IllegalArgumentException)
    }
}
