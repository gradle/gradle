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

package org.gradle.configurationcache

import groovy.lang.MissingPropertyException
import org.gradle.api.internal.project.DynamicLookupRoutine
import org.gradle.configuration.internal.DynamicCallContextTracker
import org.gradle.internal.Factory
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.metaobject.DynamicInvokeResult
import org.gradle.internal.metaobject.DynamicObject


class TrackingDynamicLookupRoutine(
    private val dynamicCallContextTracker: DynamicCallContextTracker
) : DynamicLookupRoutine {

    @Throws(MissingPropertyException::class)
    override fun property(receiver: DynamicObject, propertyName: String): Any? =
        withDynamicCall(receiver) {
            receiver.getProperty(propertyName)
        }

    override fun findProperty(receiver: DynamicObject, propertyName: String): Any? =
        withDynamicCall(receiver) {
            val dynamicInvokeResult: DynamicInvokeResult = receiver.tryGetProperty(propertyName)
            if (dynamicInvokeResult.isFound) dynamicInvokeResult.value else null
        }

    override fun setProperty(receiver: DynamicObject, name: String, value: Any?) =
        withDynamicCall(receiver) {
            receiver.setProperty(name, value)
        }

    override fun hasProperty(receiver: DynamicObject, propertyName: String): Boolean =
        withDynamicCall(receiver) {
            receiver.hasProperty(propertyName)
        }

    override fun getProperties(receiver: DynamicObject): Map<String, *>? =
        withDynamicCall(receiver) {
            DeprecationLogger.whileDisabled(Factory<Map<String, *>> { receiver.properties })
        }

    override fun invokeMethod(receiver: DynamicObject, name: String, args: Array<Any>): Any? =
        withDynamicCall(receiver) {
            receiver.invokeMethod(name, *args)
        }

    private
    fun <T> withDynamicCall(entryPoint: Any, action: () -> T): T =
        try {
            dynamicCallContextTracker.enterDynamicCall(entryPoint)
            action()
        } finally {
            dynamicCallContextTracker.leaveDynamicCall(entryPoint)
        }
}
