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

import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.ownerService
import org.gradle.configurationcache.serialization.readCollection
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.internal.event.AnonymousListenerBroadcast
import org.gradle.internal.event.ListenerManager
import org.gradle.listener.ClosureBackedMethodInvocationDispatch


internal
object ListenerBroadcastCodec : Codec<AnonymousListenerBroadcast<*>> {
    override suspend fun WriteContext.encode(value: AnonymousListenerBroadcast<*>) {
        val broadcast: AnonymousListenerBroadcast<Any> = value.uncheckedCast()
        val listenerType = value.type
        writeClass(listenerType)
        val listeners = mutableListOf<Any>()
        broadcast.visitListenersUntyped {
            val listener = this
            if (isSupportedListener(listener, listenerType)) {
                // TODO:configuration-cache consider emitting problems for unsupported listeners
                listeners.add(this)
            } else {
                logger.warn("Ignoring $listener")
            }
        }
        writeCollection(listeners) {
            write(it)
        }
    }

    override suspend fun ReadContext.decode(): AnonymousListenerBroadcast<*> {
        val type: Class<Any> = readClass().uncheckedCast()
        val listenerManager = ownerService<ListenerManager>()
        val broadcast = listenerManager.createAnonymousBroadcaster(type)
        readCollection {
            when (val listener = read()) {
                is ClosureBackedMethodInvocationDispatch -> broadcast.add(listener)
                else -> broadcast.add(listener)
            }
        }
        return broadcast
    }

    private
    fun isSupportedListener(listener: Any, listenerType: Class<out Any>) =
        listener is ClosureBackedMethodInvocationDispatch
            || listenerType.isInstance(listener)
}
