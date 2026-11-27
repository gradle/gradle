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

package org.gradle.internal.declarativedsl.mappingToJvm

import org.gradle.internal.declarativedsl.InstanceAndPublicType
import org.gradle.internal.declarativedsl.mappingToJvm.DeclarativeRuntimePropertySetter.Companion.skipSetterSpecialValue


fun interface DeclarativeRuntimePropertyGetter {
    fun getValue(receiver: Any): InstanceAndPublicType
}


/**
 * A property setter object that the DCL runtime uses to set values to properties.
 *
 * The factory function [declarativeRuntimePropertySetter] creates setter objects taking care of some parts of the contract.
 */
interface DeclarativeRuntimePropertySetter {
    /**
     * Invoke the setter and set the value of the property on the [receiver] to [value].
     * If the [value] is [skipSetterSpecialValue], the call should not have any effect.
     */
    fun setValue(receiver: Any, value: Any?)

    companion object {
        /**
         * When passed to [DeclarativeRuntimePropertySetter.setValue] as the value, the setter implementation should not set any value.
         */
        val skipSetterSpecialValue = Any()
    }
}

fun declarativeRuntimePropertySetter(setterImplementation: (receiver: Any, value: Any?) -> Unit): DeclarativeRuntimePropertySetter =
    object : DeclarativeRuntimePropertySetter {
        override fun setValue(receiver: Any, value: Any?) {
            if (value != skipSetterSpecialValue) {
                setterImplementation(receiver, value)
            }
        }
    }
