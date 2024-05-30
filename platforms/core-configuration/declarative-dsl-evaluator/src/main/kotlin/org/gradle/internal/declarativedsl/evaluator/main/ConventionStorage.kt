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

import org.gradle.internal.declarativedsl.evaluator.conventions.ConventionDefinitionRegistrar
import org.gradle.internal.declarativedsl.evaluator.conventions.SoftwareTypeConventionRepository
import org.gradle.internal.declarativedsl.evaluator.conventions.SoftwareTypeConventionResolutionResults


internal
class ConventionStorage : ConventionDefinitionRegistrar, SoftwareTypeConventionRepository {
    private
    val conventionsMap = mutableMapOf<String, SoftwareTypeConventionResolutionResults>()

    override fun registerConventions(conventionsBySoftwareType: Map<String, SoftwareTypeConventionResolutionResults>) {
        conventionsMap.clear()
        conventionsMap.putAll(conventionsBySoftwareType)
    }

    override fun findConventions(softwareTypeName: String): SoftwareTypeConventionResolutionResults? =
        conventionsMap[softwareTypeName]
}
