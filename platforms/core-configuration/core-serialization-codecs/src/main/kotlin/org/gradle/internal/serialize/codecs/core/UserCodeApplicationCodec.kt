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

package org.gradle.internal.serialize.codecs.core

import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.code.UserCodeSource
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingSharedIdentity
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.util.Path

class UserCodeApplicationCodec(
    private val userCodeApplicationContext: UserCodeApplicationContext
) : Codec<UserCodeApplicationContext.Application> {

    override suspend fun WriteContext.encode(value: UserCodeApplicationContext.Application) {
        encodePreservingSharedIdentityOf(value) {
            writeLong(value.id.longValue())
            write(value.source)
            writeTarget(value.target)
        }
    }

    private fun WriteContext.writeTarget(target: UserCodeApplicationContext.Target) {
        when (target) {
            is UserCodeApplicationContext.Target.Project -> {
                writeBoolean(true)
                writeString(target.projectIdentityPath.asString())
            }
            UserCodeApplicationContext.Target.Other.INSTANCE -> writeBoolean(false)
            else -> error("Unknown target type: ${target::class.java.name}")
        }
    }

    override suspend fun ReadContext.decode(): UserCodeApplicationContext.Application {
        return decodePreservingSharedIdentity {
            val id = readLong()
            val source = readNonNull<UserCodeSource>()
            val target = readTarget()
            userCodeApplicationContext.restoreApplication(id, source, target)
        }
    }

    private fun ReadContext.readTarget(): UserCodeApplicationContext.Target {
        if (readBoolean()) {
            return UserCodeApplicationContext.Target.Project(Path.path(readString()))
        }

        return UserCodeApplicationContext.Target.Other.INSTANCE
    }

}
