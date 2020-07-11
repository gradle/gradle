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

package org.gradle.instantexecution.serialization.codecs

import com.nhaarman.mockitokotlin2.mock
import org.gradle.instantexecution.serialization.runWriteOperation
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.kotlin.dsl.support.useToRun
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream


class ContextsTest : AbstractUserTypeCodecTest() {

    @Test
    fun `runWriteOperation can complete without recursion`() {
        val codec = codecs().userTypesCodec
        val result =
            writeContextFor(KryoBackedEncoder(ByteArrayOutputStream()), codec, mock()).useToRun {
                withIsolateMock(codec) {
                    runWriteOperation {
                        42
                    }
                }
            }
        assertThat(result, equalTo(42))
    }
}
