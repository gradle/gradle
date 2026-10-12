/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl.serialize

import org.gradle.internal.classpath.ClassLoadTimeTransform
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.classpath.TransformedClassPath
import org.gradle.internal.classpath.TransformedClassPath.InstrumentationKind
import org.gradle.internal.classpath.TransformedClassPath.TransformedEntry
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File


class ClassPathEncodingExtensionsTest {

    @Test
    fun `plain classpath round-trips`() {
        val classPath = DefaultClassPath.of(File("a.jar"), File("b.jar"))

        assertThat(roundTrip(classPath), equalTo<ClassPath>(classPath))
    }

    @Test
    fun `transformed classpath round-trips the instrumentation metadata of its entries`() {
        val classPath = TransformedClassPath.builderWithExactSize(3)
            .add(File("external.jar"), TransformedEntry(File("instrumented/instrumented-external.jar"), File("merge/analysis.bin"), InstrumentationKind.EXTERNAL_DEPENDENCY))
            .add(File("project.jar"), TransformedEntry(File("instrumented/instrumented-project.jar"), null, InstrumentationKind.PROJECT_DEPENDENCY))
            .addUntransformed(File("plain.jar"))
            .build()

        val restored = roundTrip(classPath)

        assertThat(restored, equalTo<ClassPath>(classPath))
        restored as TransformedClassPath
        assertThat(restored.findEntryFor(File("external.jar")), equalTo(TransformedEntry(File("instrumented/instrumented-external.jar"), File("merge/analysis.bin"), InstrumentationKind.EXTERNAL_DEPENDENCY)))
        assertThat(restored.findEntryFor(File("project.jar")), equalTo(TransformedEntry(File("instrumented/instrumented-project.jar"), null, InstrumentationKind.PROJECT_DEPENDENCY)))
        assertThat(restored.findEntryFor(File("plain.jar")), nullValue())
    }

    @Test
    fun `class-load-time transform is not stored`() {
        val classPath = TransformedClassPath.builderWithExactSize(1)
            .add(File("project.jar"), TransformedEntry(File("instrumented/instrumented-project.jar"), null, InstrumentationKind.PROJECT_DEPENDENCY))
            .build()
        val composed = classPath.withClassLoadTimeTransform(ClassLoadTimeTransform { _, _, classfileBuffer -> classfileBuffer })

        val restored = roundTrip(composed)

        assertThat(restored, equalTo<ClassPath>(classPath))
        restored as TransformedClassPath
        assertThat(restored.classLoadTimeTransform, nullValue())
    }

    private
    fun roundTrip(classPath: ClassPath): ClassPath {
        val bytes = ByteArrayOutputStream()
        KryoBackedEncoder(bytes).use { encoder ->
            encoder.writeClassPath(classPath)
        }
        return KryoBackedDecoder(ByteArrayInputStream(bytes.toByteArray())).readClassPath()
    }
}
