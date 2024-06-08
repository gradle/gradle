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

package org.gradle.internal.cc.impl.models

import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.connection.ToolingParameterProxy
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier


class DefaultToolingModelParameterCarrierFactory(
    private val valueSnapshotter: ValueSnapshotter
) : ToolingModelParameterCarrier.Factory {

    override fun createCarrier(parameter: Any): ToolingModelParameterCarrier {
        return Carrier(parameter, valueSnapshotter)
    }

    private
    class Carrier(
        private val parameter: Any,
        private val valueSnapshotter: ValueSnapshotter
    ) : ToolingModelParameterCarrier {

        override fun getView(viewType: Class<*>): Any {
            val viewBuilder = ProtocolToModelAdapter().builder(viewType)
            return viewBuilder.build(parameter)!!
        }

        override fun getHash(): HashCode {
            val unpacked = ToolingParameterProxy.unpackProperties(parameter)
            return Hashing.hashHashable(valueSnapshotter.snapshot(unpacked))
        }
    }
}
