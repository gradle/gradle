/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.serialize.graph.codecs

import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.serialize.graph.withBeanTrace


object BeanCodec : Codec<Any> {

    override suspend fun WriteContext.encode(value: Any) {
        encodePreservingIdentityOf(value) {
            val beanType = value.javaClass
            writeClass(beanType)
            withBeanTrace(beanType) {
                writeBeanOf(beanType, value)
            }
        }
    }

    override suspend fun ReadContext.decode(): Any =
        decodePreservingIdentity { id ->
            val beanType = readClass()
            withBeanTrace(beanType) {
                readBeanOf(beanType, id)
            }
        }

    private
    suspend fun WriteContext.writeBeanOf(beanType: Class<*>, value: Any) {
        beanStateWriterFor(beanType).run {
            writeStateOf(value)
        }
    }

    private
    suspend fun ReadContext.readBeanOf(beanType: Class<*>, id: Int): Any =
        beanStateReaderFor(beanType).run {
            newBeanWithId(id).also { bean ->
                check(beanType === bean.javaClass) {
                    "Expected bean type to be '$beanType' but it was '${bean.javaClass}'"
                }
                readStateOf(bean)
            }
        }
}
