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

package org.gradle.integtests.fixtures.modes

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo

import static ToBeFixedForConfigurationCacheExtension.isEnabledBottomSpec

class ToBeFixedForIsolatedProjectsExtension implements IAnnotationDrivenExtension<ToBeFixedForIsolatedProjects> {

    private final ToBeFixedSpecInterceptor toBeFixedSpecInterceptor = new ToBeFixedSpecInterceptor("Isolated Projects")

    @Override
    void visitSpecAnnotation(ToBeFixedForIsolatedProjects annotation, SpecInfo spec) {
        if (GradleContextualExecuter.isNotIsolatedProjects()) {
            return
        }

        if (!isEnabledBottomSpec(annotation.bottomSpecs(), { spec.bottomSpec.name == it })) {
            return
        }

        if (!annotation.skipBecause().isEmpty()) {
            spec.skipped = true
            return
        }

        toBeFixedSpecInterceptor.intercept(spec, annotation.iterationMatchers())
    }

    @Override
    void visitFeatureAnnotation(ToBeFixedForIsolatedProjects annotation, FeatureInfo feature) {
        if (GradleContextualExecuter.isNotIsolatedProjects()) {
            return
        }

        if (!isEnabledBottomSpec(annotation.bottomSpecs(), { feature.spec.bottomSpec.name == it })) {
            return
        }

        if (!annotation.skipBecause().isEmpty()) {
            feature.skipped = true
            return
        }

        toBeFixedSpecInterceptor.intercept(feature, annotation.iterationMatchers())
    }
}
