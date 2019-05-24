/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution


internal
object Tags {

    // JVM types
    const val NULL_VALUE: Byte = 0
    const val STRING_TYPE: Byte = 1
    const val BOOLEAN_TYPE: Byte = 2
    const val BYTE_TYPE: Byte = 3
    const val INT_TYPE: Byte = 4
    const val SHORT_TYPE: Byte = 5
    const val LONG_TYPE: Byte = 6
    const val FLOAT_TYPE: Byte = 7
    const val DOUBLE_TYPE: Byte = 8
    const val LIST_TYPE: Byte = 9
    const val SET_TYPE: Byte = 10
    const val MAP_TYPE: Byte = 11
    const val FILE_TYPE: Byte = 12
    const val CLASS_TYPE: Byte = 13
    const val BEAN: Byte = 14

    // Logging type
    const val LOGGER_TYPE: Byte = 40

    // Gradle types
    const val THIS_TASK: Byte = 80
    const val FILE_TREE_TYPE: Byte = 81
    const val FILE_COLLECTION_TYPE: Byte = 82
    const val ARTIFACT_COLLECTION_TYPE: Byte = 83
    const val OBJECT_FACTORY_TYPE: Byte = 84

    // Internal Gradle types
    const val FILE_RESOLVER_TYPE: Byte = 90
    const val PATTERN_SPEC_FACTORY_TYPE: Byte = 91
    const val FILE_COLLECTION_FACTORY_TYPE: Byte = 92
    const val INSTANTIATOR_TYPE: Byte = 93
    const val FILE_OPERATIONS_TYPE: Byte = 94
    const val DEFAULT_COPY_SPEC: Byte = 95
    const val DESTINATION_ROOT_COPY_SPEC: Byte = 96
}
