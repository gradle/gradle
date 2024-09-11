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

package org.gradle.internal.declarativedsl.analysis.transformation

import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.ParameterValueBinding


/**
 * Utilities for replacing receivers in an ObjectOrigin hierarchy.
 */
object OriginReplacement {
    /**
     * Replaces receivers matching a predicate with a replacement receiver.  The replacement returns a deep copy of the receiver hierarchy of the
     * supplied {@link ObjectOrigin} with the given replacement applied.
     */
    fun replaceReceivers(replaceIn: ObjectOrigin, predicate: (ObjectOrigin.ReceiverOrigin) -> Boolean, replacement: ObjectOrigin.ReceiverOrigin): ObjectOrigin =
        ReceiverReplacementContext(predicate, replacement).replace(replaceIn)

    private
    class ReceiverReplacementContext(val predicate: (ObjectOrigin.ReceiverOrigin) -> Boolean, val replacement: ObjectOrigin.ReceiverOrigin) {
        fun replace(origin: ObjectOrigin): ObjectOrigin =
            when (origin) {
                is ObjectOrigin.ReceiverOrigin -> replaceInReceiver(origin)
                is ObjectOrigin.FunctionOrigin -> replaceInFunction(origin)

                is ObjectOrigin.CustomConfigureAccessor -> origin.copy(receiver = replace(origin.receiver))
                is ObjectOrigin.FromLocalValue -> origin.copy(assigned = replace(origin.assigned))
                is ObjectOrigin.ImplicitThisReceiver -> origin.copy(resolvedTo = replaceInReceiver(origin.resolvedTo))
                is ObjectOrigin.PropertyDefaultValue -> origin.copy(receiver = replace(origin.receiver))
                is ObjectOrigin.PropertyReference -> origin.copy(receiver = replace(origin.receiver))

                is ObjectOrigin.ConstantOrigin,
                is ObjectOrigin.External,
                is ObjectOrigin.NullObjectOrigin -> origin
            }

        private
        fun replaceInReceiver(receiverOrigin: ObjectOrigin.ReceiverOrigin) =
            if (predicate(receiverOrigin)) replacement else when (receiverOrigin) {
                is ObjectOrigin.AddAndConfigureReceiver -> replaceIn(receiverOrigin)
                is ObjectOrigin.AccessAndConfigureReceiver -> replaceIn(receiverOrigin)
                is ObjectOrigin.ConfiguringLambdaReceiver -> receiverOrigin.copy(receiver = replace(receiverOrigin.receiver))
                is ObjectOrigin.TopLevelReceiver -> receiverOrigin
            }

        private
        fun replaceInFunction(origin: ObjectOrigin.FunctionOrigin): ObjectOrigin.FunctionOrigin = when (origin) {
            is ObjectOrigin.AddAndConfigureReceiver -> replaceIn(origin)
            is ObjectOrigin.AccessAndConfigureReceiver -> replaceIn(origin)
            is ObjectOrigin.BuilderReturnedReceiver -> origin.copy(receiver = replace(origin.receiver), parameterBindings = replaceInArgs(origin.parameterBindings))
            is ObjectOrigin.ConfiguringLambdaReceiver -> origin.copy(receiver = replace(origin.receiver), parameterBindings = replaceInArgs(origin.parameterBindings))
            is ObjectOrigin.NewObjectFromMemberFunction -> origin.copy(receiver = replace(origin.receiver), parameterBindings = replaceInArgs(origin.parameterBindings))
            is ObjectOrigin.NewObjectFromTopLevelFunction -> origin.copy(parameterBindings = replaceInArgs(origin.parameterBindings))
        }

        private
        fun replaceIn(addAndConfigureReceiver: ObjectOrigin.AddAndConfigureReceiver) =
            addAndConfigureReceiver.copy(
                receiver = replaceInFunction(addAndConfigureReceiver.receiver)
            )

        private
        fun replaceIn(accessAndConfigureReceiver: ObjectOrigin.AccessAndConfigureReceiver) =
            accessAndConfigureReceiver.copy(
                receiver = replace(accessAndConfigureReceiver.receiver),
                parameterBindings = replaceInArgs(accessAndConfigureReceiver.parameterBindings)
            )

        private
        fun replaceInArgs(
            parameterValueBinding: ParameterValueBinding,
        ): ParameterValueBinding =
            parameterValueBinding.copy(parameterValueBinding.bindingMap.mapValues { replace(it.value) })
    }
}
