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

package org.gradle.internal.declarativedsl.evaluator.main

import org.gradle.internal.declarativedsl.evaluator.defaults.ModelDefaultsDefinitionRegistrar
import org.gradle.internal.declarativedsl.evaluator.defaults.ModelDefaultsRepository
import org.gradle.internal.declarativedsl.evaluator.defaults.ModelDefaultsResolutionResults


internal
class ModelDefaultsStorage : ModelDefaultsDefinitionRegistrar, ModelDefaultsRepository {
    private
    val modelDefaultsMap = mutableMapOf<String, ModelDefaultsResolutionResults>()

    override fun registerDefaults(modelDefaultsBySoftwareType: Map<String, ModelDefaultsResolutionResults>) {
        modelDefaultsMap.clear()
        modelDefaultsMap.putAll(modelDefaultsBySoftwareType)
    }

    override fun findDefaults(softwareTypeName: String): ModelDefaultsResolutionResults? =
        modelDefaultsMap[softwareTypeName]
}
