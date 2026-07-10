/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.fixtures.compatibility

import org.gradle.test.fixtures.Flaky
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.model.SpecInfo

/**
 * This is a workaround for https://github.com/gradle/gradle-private/issues/4948.
 * Spock's runner configuration only supports OR logic when multiple annotations are included via system properties.
 * This extension provides the necessary AND logic to properly filter tests.
 *
 * The extension handles two scenarios:
 *
 * - -Dinclude.spock.annotation=Flaky,MultiVersionTestCategory
 *   Runs only specs with both annotations
 *
 * - -Dinclude.spock.annotation=MultiVersionTestCategory -Dexclude.spock.annotation=Flaky
 *   Runs specs with MultiVersionTestCategory but without Flaky
 */
class MultiVersionTestCategoryExtension implements IAnnotationDrivenExtension<MultiVersionTestCategory> {
    @Override
    void visitSpecAnnotation(MultiVersionTestCategory annotation, SpecInfo spec) {
        def excludeProp = System.getProperty("exclude.spock.annotation")
        def includeProp = System.getProperty("include.spock.annotation")

        def flakyClassName = Flaky.class.name
        def multiVersionClassName = MultiVersionTestCategory.class.name

        // Check if spec has Flaky annotation (at class or feature method level)
        def specHasFlaky = spec.getAnnotationsByType(Flaky.class).length > 0
        def anyFeatureHasFlaky = spec.getAllFeatures().any { feature ->
            def method = feature.getFeatureMethod()
            if (method != null) {
                try {
                    return method.getReflection().isAnnotationPresent(Flaky.class)
                } catch (UnsupportedOperationException e) {
                    // getReflection() not supported for some feature types (e.g. data-driven)
                    return false
                }
            }
            return false
        }
        def hasFlaky = specHasFlaky || anyFeatureHasFlaky

        // Case 1: When "exclude.spock.annotation" has "Flaky", exclude all such tests marked with Flaky
        if (excludeProp != null) {
            def excludeAnnotations = excludeProp.split(",").collect { it.trim() }
            if (excludeAnnotations.contains(flakyClassName) && hasFlaky) {
                spec.setSkipped(true)
                return
            }
        }

        // Case 2: When "include.spock.annotation" has both, exclude the tests not marked with Flaky
        if (includeProp != null) {
            def includeAnnotations = includeProp.split(",").collect { it.trim() }
            def hasFlakyInInclude = includeAnnotations.contains(flakyClassName)
            def hasMultiVersionInInclude = includeAnnotations.contains(multiVersionClassName)

            if (hasFlakyInInclude && hasMultiVersionInInclude && !hasFlaky) {
                // Both are in include list, but this spec doesn't have Flaky, so skip it
                spec.setSkipped(true)
                return
            }
        }
    }
}

