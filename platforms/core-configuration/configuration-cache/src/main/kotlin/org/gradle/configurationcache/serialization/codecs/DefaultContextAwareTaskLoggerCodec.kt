/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.Task
import org.gradle.api.logging.Logging
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.logging.slf4j.ContextAwareTaskLogger.MessageRewriter
import org.gradle.internal.logging.slf4j.DefaultContextAwareTaskLogger


object DefaultContextAwareTaskLoggerCodec : Codec<DefaultContextAwareTaskLogger> {

    override suspend fun WriteContext.encode(value: DefaultContextAwareTaskLogger) {
        encodePreservingIdentityOf(value) {
            write(it.messageRewriter)
        }
    }

    override suspend fun ReadContext.decode(): DefaultContextAwareTaskLogger =
        decodePreservingIdentity { id ->
            DefaultContextAwareTaskLogger(Logging.getLogger(Task::class.java)).also { logger ->
                read()?.let { rewriter ->
                    logger.messageRewriter = rewriter as MessageRewriter
                }
                isolate.identities.putInstance(id, logger)
            }
        }
}
