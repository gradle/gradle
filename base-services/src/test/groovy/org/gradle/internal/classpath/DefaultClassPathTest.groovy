/*
 * Copyright 2012 the original author or authors.
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

import spock.lang.Specification
import org.gradle.util.Matchers

class DefaultClassPathTest extends Specification {
    def "removes duplicates when constructed"() {
        def file1 = new File("a.jar")
        def file2 = new File("b.jar")
        def cp1 = DefaultClassPath.of(file1, file2, file1, file2)
        def cp2 = DefaultClassPath.of([file1, file1, file2, file1])

        expect:
        cp1.asFiles == [file1, file2]
        cp2.asFiles == [file1, file2]
    }

    def "can add classpaths together"() {
        def file1 = new File("a.jar")
        def file2 = new File("b.jar")
        def cp1 = DefaultClassPath.of(file1)
        def cp2 = DefaultClassPath.of(file2)

        expect:
        def cp3 = cp1 + cp2
        cp3.asFiles == [file1, file2]
    }

    def "removes duplicates when added together"() {
        def file1 = new File("a.jar")
        def file2 = new File("b.jar")
        def file3 = new File("c.jar")
        def cp1 = DefaultClassPath.of(file1, file2)
        def cp2 = DefaultClassPath.of(file3, file2, file1)

        expect:
        def cp3 = cp1 + cp2
        cp3.asFiles == [file1, file2, file3]
    }

    def "add returns lhs when rhs is empty"() {
        def cp1 = DefaultClassPath.of(new File("a.jar"))
        def cp2 = ClassPath.EMPTY

        expect:
        (cp1 + cp2).is(cp1)
    }

    def "add returns rhs when lhs is empty"() {
        def cp1 = ClassPath.EMPTY
        def cp2 = DefaultClassPath.of(new File("a.jar"))

        expect:
        (cp1 + cp2).is(cp2)
    }

    def "can add collection of files to classpath"() {
        def file1 = new File("a.jar")
        def file2 = new File("b.jar")
        def cp = DefaultClassPath.of(file1)

        expect:
        (cp + [file2]).asFiles == [file1, file2]
        (cp + []).is(cp)
    }

    def "removes files based on predicate"() {
        given:
        def a1 = new File("a1.jar")
        def b = new File("b.jar")
        def a2 = new File("a2.jar")
        def c = new File("c.jar")

        when:
        def originalClasspath = DefaultClassPath.of(a1, b, a2, c)
        def newClasspath = originalClasspath.removeIf { it.name.startsWith("a") }

        then:
        originalClasspath.asFiles == [a1, b, a2, c]
        newClasspath.asFiles == [b, c]
    }

    def "returns same classpath when predicate to remove does not match"() {
        given:
        def a = new File("a.jar")
        def b = new File("b.jar")

        when:
        def originalClasspath = DefaultClassPath.of(a, b)
        def newClasspath = originalClasspath.removeIf { it.name.startsWith("c") }

        then:
        originalClasspath.asFiles == [a, b]
        newClasspath.is(originalClasspath)
    }

    def "classpaths are equal when the contain the same sequence of files"() {
        def file1 = new File("a.jar")
        def file2 = new File("b.jar")
        def file3 = new File("c.jar")
        def cp = DefaultClassPath.of(file1, file2)
        def same = DefaultClassPath.of(file1, file2)
        def sameWithDuplicates = DefaultClassPath.of(file1, file2, file2, file1)
        def differentOrder = DefaultClassPath.of(file2, file1)
        def missing = DefaultClassPath.of(file2)
        def extra = DefaultClassPath.of(file1, file2, file3)
        def different = DefaultClassPath.of(file3)

        expect:
        cp Matchers.strictlyEqual(same)
        cp Matchers.strictlyEqual(sameWithDuplicates)
        cp != differentOrder
        cp != missing
        cp != extra
        cp != different
    }
}
