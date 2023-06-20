/*
 * Copyright 2022 the original author or authors.
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

class TransformedClassPathTest extends Specification {
    def "transformed jars are returned when present"() {
        given:
        TransformedClassPath cp = transformedClassPath("original.jar": "transformed.jar")

        expect:
        cp.findTransformedJarFor(file("original.jar")) == file("transformed.jar")
    }

    def "transformed jars are returned in the list of transformed files"() {
        given:
        TransformedClassPath cp = transformedClassPath("original.jar": "transformed.jar")

        expect:
        cp.asTransformedFiles == [file("transformed.jar")]
    }

    def "original jars are returned in the list of original jars"() {
        given:
        TransformedClassPath cp = transformedClassPath("original.jar": "transformed.jar")

        expect:
        cp.asFiles == [file("original.jar")]
        cp.asURIs == [file("original.jar").toURI()]
        cp.asURLs == [file("original.jar").toURI().toURL()]
        cp.asURLArray == [file("original.jar").toURI().toURL()].toArray()
    }

    def "transformed classpath can be mixed with non-transformed one"() {
        given:
        TransformedClassPath transformed = transformedClassPath("1.jar": "t1.jar")
        ClassPath nonTransformed = DefaultClassPath.of(file("2.jar"))

        when:
        def combined = transformed + nonTransformed

        then:
        combined.asFiles == [file("1.jar"), file("2.jar")]
        combined.asTransformedFiles == [file("t1.jar"), file("2.jar")]

        combined.findTransformedJarFor(file("1.jar")) == file("t1.jar")
        combined.findTransformedJarFor(file("2.jar")) == null
    }

    def "transformed jars override appended non-transformed ones"() {
        given:
        TransformedClassPath transformed = transformedClassPath("1.jar": "t1.jar")
        ClassPath nonTransformed = DefaultClassPath.of(file("1.jar"))

        when:
        def combined = transformed + nonTransformed

        then:
        combined.asFiles == [file("1.jar")]
        combined.asTransformedFiles == [file("t1.jar")]

        combined.findTransformedJarFor(file("1.jar")) == file("t1.jar")
    }

    def "transformed classpath can be appended to another transformed"() {
        given:
        TransformedClassPath transformed1 = transformedClassPath("1.jar": "t1.jar")
        TransformedClassPath transformed2 = transformedClassPath("2.jar": "t2.jar")

        when:
        def combined = transformed1 + transformed2

        then:
        combined.asFiles == [file("1.jar"), file("2.jar")]
        combined.asTransformedFiles == [file("t1.jar"), file("t2.jar")]

        combined.findTransformedJarFor(file("1.jar")) == file("t1.jar")
    }

    def "first transform on the classpath wins"() {
        given:
        TransformedClassPath transformed1 = transformedClassPath("1.jar": "t1.jar")
        TransformedClassPath transformed2 = transformedClassPath("1.jar": "t2.jar")

        when:
        def combined = transformed1 + transformed2

        then:
        combined.asFiles == [file("1.jar")]
        combined.asTransformedFiles == [file("t1.jar")]

        combined.findTransformedJarFor(file("1.jar")) == file("t1.jar")
    }

    def "transformed classpath can be prepended to non-transformed"() {
        given:
        TransformedClassPath transformed = transformedClassPath("1.jar": "t1.jar", "2.jar": "t2.jar")
        ClassPath nonTransformed = DefaultClassPath.of(file("1.jar"))

        when:
        def combined = nonTransformed + transformed

        then:
        combined.asFiles == [file("1.jar"), file("2.jar")]
        (combined as TransformedClassPath).asTransformedFiles == [file("1.jar"), file("t2.jar")]

        combined.findTransformedJarFor(file("1.jar")) == null
        combined.findTransformedJarFor(file("2.jar")) == file("t2.jar")
    }

    def "non-transformed jar on transformed classpath stays non-transformed when another transformation is appended"() {
        given:
        ClassPath nonTransformed = DefaultClassPath.of(file("1.jar"))
        TransformedClassPath transformed1 = transformedClassPath("2.jar": "t2.jar")
        TransformedClassPath transformed2 = transformedClassPath("1.jar": "t1.jar")

        when:
        def combined = (nonTransformed + transformed1) + transformed2

        then:
        combined.asFiles == [file("1.jar"), file("2.jar")]
        (combined as TransformedClassPath).asTransformedFiles == [file("1.jar"), file("t2.jar")]

        combined.findTransformedJarFor(file("1.jar")) == null
        combined.findTransformedJarFor(file("2.jar")) == file("t2.jar")
    }

    def "getting transform for a file outside of the classpath is fine"() {
        given:
        TransformedClassPath cp = transformedClassPath("1.jar": "t1.jar")

        expect:
        cp.findTransformedJarFor(file("2.jar")) == null
    }

    def "removeIf is applied to original jars"() {
        given:
        TransformedClassPath cp = transformedClassPath("1.jar": "t1.jar", "2.jar": "t2.jar")

        when:
        TransformedClassPath filtered = cp.removeIf { it == file("1.jar") }

        then:
        filtered.asFiles == [file("2.jar")]
        filtered.findTransformedJarFor(file("1.jar")) == null
    }

    def "removeIf is not applied to transformed jars"() {
        given:
        TransformedClassPath cp = transformedClassPath("1.jar": "t1.jar", "2.jar": "t2.jar")

        when:
        TransformedClassPath filtered = cp.removeIf { it == file("t1.jar") }

        then:
        filtered.asFiles == [file("1.jar"), file("2.jar")]
        filtered.findTransformedJarFor(file("1.jar")) == file("t1.jar")
    }

    def "instrumenting artifact transform output can be converted to classpath"() {
        when:
        TransformedClassPath cp = TransformedClassPath.fromInstrumentingArtifactTransformOutput(inputClassPath)

        then:
        cp.asFiles == outputClassPath.asFiles
        cp.findTransformedJarFor(file(original)) == (transformed != null ? file(transformed) : null)

        where:
        inputClassPath                                  | outputClassPath             | original | transformed
        classPath("1.jiar", "1.jar")                    | classPath("1.jar")          | "1.jar"  | "1.jiar"
        classPath("1.jiar", "1.jar", "2.jar")           | classPath("1.jar", "2.jar") | "1.jar"  | "1.jiar"
        classPath("1.jiar", "1.jar", "2.jar")           | classPath("1.jar", "2.jar") | "2.jar"  | null
        classPath("1.jar", "2.jiar", "2.jar")           | classPath("1.jar", "2.jar") | "2.jar"  | "2.jiar"
        classPath("1.jar", "2.jiar", "2.jar")           | classPath("1.jar", "2.jar") | "1.jar"  | null
        classPath("1.jiar", "1.jar", "2.jiar", "2.jar") | classPath("1.jar", "2.jar") | "1.jar"  | "1.jiar"
        classPath("1.jiar", "1.jar", "2.jiar", "2.jar") | classPath("1.jar", "2.jar") | "2.jar"  | "2.jiar"

        classPath("1.jar")                              | classPath("1.jar")          | "1.jar"  | null
        classPath("1.jar", "2.jar")                     | classPath("1.jar", "2.jar") | "1.jar"  | null
        classPath("1.jar", "2.jar")                     | classPath("1.jar", "2.jar") | "2.jar"  | null
    }

    def "invalid instrumenting artifact transform outputs are detected"() {
        when:
        TransformedClassPath.fromInstrumentingArtifactTransformOutput(inputClassPath)

        then:
        thrown IllegalArgumentException

        where:
        inputClassPath                          | _
        transformedClassPath("1.jar": "1.jiar") | _
        classPath("1.jiar")                     | _
        classPath("1.jiar", "2.jiar")           | _
        classPath("1.jar", "1.jiar")            | _
    }

    def "instrumenting artifact transform output is recognized"() {
        when:
        ClassPath cp = TransformedClassPath.handleInstrumentingArtifactTransform(inputClassPath)

        then:
        cp instanceof TransformedClassPath
        cp.asFiles == outputClassPath.asFiles

        where:
        inputClassPath                                  | outputClassPath
        classPath("1.jiar", "1.jar")                    | classPath("1.jar")
        classPath("1.jiar", "1.jar", "2.jar")           | classPath("1.jar", "2.jar")
        classPath("1.jiar", "1.jar", "2.jar")           | classPath("1.jar", "2.jar")
        classPath("1.jar", "2.jiar", "2.jar")           | classPath("1.jar", "2.jar")
        classPath("1.jar", "2.jiar", "2.jar")           | classPath("1.jar", "2.jar")
        classPath("1.jiar", "1.jar", "2.jiar", "2.jar") | classPath("1.jar", "2.jar")
        classPath("1.jiar", "1.jar", "2.jiar", "2.jar") | classPath("1.jar", "2.jar")
    }

    def "not instrumenting artifact transform output is recognized"() {
        when:
        ClassPath cp = TransformedClassPath.handleInstrumentingArtifactTransform(inputClassPath)

        then:
        !(cp instanceof TransformedClassPath)
        cp.asFiles == outputClassPath.asFiles

        where:
        inputClassPath                                  | outputClassPath
        classPath("1.jar")                              | classPath("1.jar")
        classPath("1.jar", "2.jar")                     | classPath("1.jar", "2.jar")
        classPath("1.jar", "2.jar")                     | classPath("1.jar", "2.jar")
        ClassPath.EMPTY                                 | ClassPath.EMPTY
    }

    def "transformedclasspath is recognized"() {
        when:
        ClassPath cp = TransformedClassPath.handleInstrumentingArtifactTransform(inputClassPath)

        then:
        cp instanceof TransformedClassPath
        cp.asFiles == outputClassPath.asFiles

        where:
        inputClassPath                          | outputClassPath
        transformedClassPath("1.jar": "1.jiar") | classPath("1.jar")
        transformedClassPath("1.jiar": "1.jar") | classPath("1.jiar")
    }

    private static File file(String path) {
        return new File(path)
    }

    private static TransformedClassPath transformedClassPath(Map<String, String> jarMapping) {
        def builder = TransformedClassPath.builderWithExactSize(jarMapping.size())
        jarMapping.forEach { original, transformed ->
            builder.add(file(original), file(transformed))
        }
        return builder.build()
    }

    private static ClassPath classPath(String... jars) {
        return DefaultClassPath.of(jars.collect { file(it) })
    }
}
