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

package org.gradle.internal.declarativedsl.dom.mutation

import java.util.Collections


interface MutationDefinitionCatalog {
    fun registerMutationDefinition(definition: MutationDefinition)
    val mutationDefinitionsById: Map<String, MutationDefinition>
}


class DefaultMutationDefinitionCatalog : MutationDefinitionCatalog {
    private
    val mutationDefinitions: MutableMap<String, MutationDefinition> = mutableMapOf()

    override fun registerMutationDefinition(definition: MutationDefinition) {
        val id = definition.id
        require(mutationDefinitions.putIfAbsent(id, definition) == null) { "mutation with ID $id has already been registered" }
    }

    override val mutationDefinitionsById: Map<String, MutationDefinition>
        get() = Collections.unmodifiableMap(mutationDefinitions)
}
