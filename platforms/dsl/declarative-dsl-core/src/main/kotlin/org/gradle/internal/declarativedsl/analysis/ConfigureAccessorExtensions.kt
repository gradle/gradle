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

package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.schema.ConfigureAccessor


fun ConfigureAccessor.access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin = when (this) {
    is ConfigureAccessor.Property ->
        ObjectOrigin.PropertyReference(objectOrigin, dataProperty, objectOrigin.originElement)
    is ConfigureAccessor.Custom ->
        ObjectOrigin.CustomConfigureAccessor(objectOrigin, this, objectOrigin.originElement)
    is ConfigureAccessor.ConfiguringLambdaArgument ->
        ObjectOrigin.ConfiguringLambdaReceiver(inFunction.function, inFunction.parameterBindings, inFunction.invocationId, objectType, inFunction.originElement, inFunction.receiver)
}
