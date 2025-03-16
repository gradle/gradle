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

package org.gradle.internal.cc.impl.serialize

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.BuildIdentifierSerializer
import org.gradle.internal.serialize.codecs.core.JavaRecordCodec
import org.gradle.internal.serialize.codecs.guava.guavaTypes
import org.gradle.internal.serialize.codecs.stdlib.stdlibTypes
import org.gradle.internal.serialize.graph.codecs.BindingsBuilder


internal
fun BindingsBuilder.baseTypes() {
    stdlibTypes()
    guavaTypes()
    bind(JavaRecordCodec)
    bind(BuildIdentifierSerializer())
}
