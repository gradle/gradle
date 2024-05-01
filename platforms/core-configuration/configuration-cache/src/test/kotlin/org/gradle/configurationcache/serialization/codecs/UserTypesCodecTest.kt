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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.configurationcache.problems.PropertyTrace
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.beans.Optimizable
import org.gradle.configurationcache.serialization.readNonNull
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.io.Writer
import kotlin.random.Random

enum class FieldType {
    Int, String, Bean
}

typealias BeanSchema = List<FieldType>

class UserTypesCodecTest : AbstractUserTypeCodecTest() {

    class Foo(var i: Int, var s: String, var bar: Bar) : Optimizable {
        override suspend fun ReadContext.readBean() {
            val _bar = readNonNull<Bar>()
            skipTag()
            val _i = readInt()
            skipTag()
            val _s = readString()
            i = _i
            s = _s
            bar = _bar
        }
    }

    data class Bar(val i: Int)

    fun Random.generateSchema(): BeanSchema {
        val maxLength = 20
        val fieldsCount = nextInt(2, maxLength)
        return List(fieldsCount) {
            FieldType.values().random(this)
        }
    }

    fun Writer.writeType(schema: BeanSchema) {
        val name = schema.name
        write(
            """
            class $name(${fieldsOf(schema)}) : Optimizable {

                override suspend fun ReadContext.readBean() {"""
        )

        schema.forEachIndexed { index, fieldType ->
            when (fieldType) {
                FieldType.Int -> appendLine("skipTag();_$index=readInt()")
                FieldType.String -> appendLine("skipTag();_$index=readString()")
                FieldType.Bean -> appendLine("_$index=read()?.uncheckedCast()")
            }
        }
        write(
            """

                }
            }
        """
        )
    }

    private fun fieldsOf(schema: BeanSchema): String {
        return schema.mapIndexed { index, fieldType ->
            when (fieldType) {
                FieldType.Int -> "var _$index: Int"
                FieldType.String -> "var _$index: String"
                FieldType.Bean -> "var _$index: Bean"
            }
        }.joinToString()
    }

    private val BeanSchema.name: String
        get() = joinToString("") {
            when (it) {
                FieldType.Int -> "I"
                FieldType.String -> "S"
                FieldType.Bean -> "B"
            }
        }

    @Test
    fun `generate`() {
        OutputStreamWriter(System.out).use { writer ->
            repeat(1) {
                val schema = Random.generateSchema()
                writer.writeType(schema)
            }
        }
    }

    @Test
    fun `can handle optimizable bean`() {
        val foo = Foo(42, "foo", Bar(43))

        val read = configurationCacheRoundtripOf(foo)

        assertThat(
            read.bar,
            equalTo(foo.bar)
        )

        assertThat(
            read.i,
            equalTo(foo.i)
        )

        assertThat(
            read.s,
            equalTo(foo.s)
        )
    }


    @Test
    fun `can handle deeply nested graphs`() {

        val deepGraph = Peano.fromInt(1024)

        val read = configurationCacheRoundtripOf(deepGraph)

        assertThat(
            read.toInt(),
            equalTo(deepGraph.toInt())
        )
    }

    @Test
    fun `internal types codec leaves not implemented trace for unsupported types`() {

        val unsupportedBean = 42 to "42"
        val problems = serializationProblemsOf(unsupportedBean, codecs().internalTypesCodec())
        val problem = problems.single()
        assertInstanceOf<PropertyTrace.Gradle>(
            problem.trace
        )
        assertThat(
            problem.message.toString(),
            equalTo("objects of type 'kotlin.Pair' are not yet supported with the configuration cache.")
        )
    }

    @Test
    fun `can handle anonymous enum subtypes`() {
        EnumSuperType.values().forEach {
            assertThat(
                configurationCacheRoundtripOf(it),
                sameInstance(it)
            )
        }
    }

    enum class EnumSuperType {

        SubType1 {
            override fun displayName() = "one"
        },
        SubType2 {
            override fun displayName() = "two"
        };

        abstract fun displayName(): String
    }

    @Test
    fun `preserves identity of java util logging Level`() {
        configurationCacheRoundtripOf(java.util.logging.Level.INFO to java.util.logging.Level.WARNING).run {
            assertThat(
                first,
                sameInstance(java.util.logging.Level.INFO)
            )
            assertThat(
                second,
                sameInstance(java.util.logging.Level.WARNING)
            )
        }
    }

    @Test
    fun `Peano sanity check`() {

        assertThat(
            Peano.fromInt(0),
            equalTo(Peano.Z)
        )

        assertThat(
            Peano.fromInt(1024).toInt(),
            equalTo(1024)
        )
    }

    sealed class Peano {

        companion object {

            fun fromInt(n: Int): Peano = (0 until n).fold(Z as Peano) { acc, _ -> S(acc) }
        }

        fun toInt(): Int = sequence().count() - 1

        object Z : Peano() {
            override fun toString() = "Z"
        }

        data class S(val n: Peano) : Peano() {
            override fun toString() = "S($n)"
        }

        private
        fun sequence() = generateSequence(this) { previous ->
            when (previous) {
                is Z -> null
                is S -> previous.n
            }
        }
    }
}

private fun ReadContext.skipTag() {
    readSmallInt()
}
