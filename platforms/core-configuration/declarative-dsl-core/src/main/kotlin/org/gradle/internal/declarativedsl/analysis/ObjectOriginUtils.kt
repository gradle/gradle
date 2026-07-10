/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.declarativedsl.analysis

import org.gradle.internal.declarativedsl.analysis.ObjectOrigin.DelegatingObjectOrigin

/**
 * Shortens all delegate references in the object origins, making sure that different origins pointing to
 * the same object (or opaque origin, like a property reference) are represented in the same way.
 */
fun ObjectOrigin.canonical(): ObjectOrigin = when (this) {
    is DelegatingObjectOrigin -> delegate.canonical()

    is ObjectOrigin.PropertyReference -> copy(receiver = receiver.canonical())
    is ObjectOrigin.PropertyDefaultValue -> copy(receiver = receiver.canonical())

    is ObjectOrigin.CustomConfigureAccessor -> copy(receiver = receiver.canonical())
    is ObjectOrigin.ConfiguringLambdaReceiver -> copy(receiver = receiver.canonical(), parameterBindings = canonicalizeParameterValueBinding(parameterBindings))
    is ObjectOrigin.NewObjectFromMemberFunction -> copy(receiver = receiver.canonical(), parameterBindings = canonicalizeParameterValueBinding(parameterBindings))
    is ObjectOrigin.NewObjectFromTopLevelFunction -> copy(parameterBindings = canonicalizeParameterValueBinding(parameterBindings))

    is ObjectOrigin.GroupedVarargValue -> copy(elementValues = elementValues.map(ObjectOrigin::canonical))

    is ObjectOrigin.External,
    is ObjectOrigin.EnumConstantOrigin,
    is ObjectOrigin.NullObjectOrigin,
    is ObjectOrigin.TopLevelReceiver,
    is ObjectOrigin.ConstantOrigin -> this
}

private fun canonicalizeParameterValueBinding(parameterValueBinding: ParameterValueBinding) =
    parameterValueBinding.copy(parameterValueBinding.bindingMap.mapValues { (_, arg) -> arg.copy(objectOrigin = arg.objectOrigin.canonical()) })
