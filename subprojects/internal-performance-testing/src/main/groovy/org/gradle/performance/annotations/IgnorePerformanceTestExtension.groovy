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

package org.gradle.performance.annotations


import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo

class IgnorePerformanceTestExtension implements IAnnotationDrivenExtension<IgnorePerformanceTest> {
    public static final String DEFAULT_REASON = "Ignored via @Ignore";

    @Override
    public void visitSpecAnnotation(IgnorePerformanceTest ignore, SpecInfo spec) {
        if (!RunForExtension.writingPerformanceJson && spec.getIsBottomSpec()) {
            spec.skip(ignore.value().isEmpty() ? DEFAULT_REASON : ignore.value())
        }
    }

    @Override
    public void visitFeatureAnnotation(IgnorePerformanceTest ignore, FeatureInfo feature) {
        if (!RunForExtension.writingPerformanceJson) {
            feature.skip(ignore.value().isEmpty() ? DEFAULT_REASON : ignore.value())
        }
    }
}
