/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.declarativedsl.features

import org.gradle.api.internal.DynamicObjectAware
import org.gradle.features.internal.binding.ProjectFeatureSupportInternal
import org.gradle.internal.declarativedsl.evaluator.conversion.ReflectionToConversionResultHandler
import org.gradle.internal.declarativedsl.mappingToJvm.ReflectionToConversionResult

/**
 * A {@link ReflectionToConversionResultHandler} that walks through the graph of features and applies them.
 */
class ProjectFeatureApplicationReflectionToConversionResultHandler : ReflectionToConversionResultHandler {
    override fun processReflectionToConversionResult(reflectionToConversionResult: ReflectionToConversionResult) {
        // Find the resolved object at the root of the feature graph and then walk the features, applying each
        val topLevelReflection = reflectionToConversionResult.topLevelObjectReflection
        val root = reflectionToConversionResult.resolvedObjectGraph.getObjectByResolvedOrigin(topLevelReflection.objectOrigin)
        if (root.instance is DynamicObjectAware) {
            ProjectFeatureSupportInternal.walkAndApplyFeatures(root.instance!!)
        }
    }
}
