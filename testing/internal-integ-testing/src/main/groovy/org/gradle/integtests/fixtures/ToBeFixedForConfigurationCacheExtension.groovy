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

package org.gradle.integtests.fixtures

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.model.FeatureInfo

import java.util.function.Predicate

import static org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache.Skip.DO_NOT_SKIP

class ToBeFixedForConfigurationCacheExtension implements IAnnotationDrivenExtension<ToBeFixedForConfigurationCache> {

    private final ToBeFixedSpecInterceptor toBeFixedSpecInterceptor = new ToBeFixedSpecInterceptor("Configuration Cache")

    @Override
    void visitFeatureAnnotation(ToBeFixedForConfigurationCache annotation, FeatureInfo feature) {

        if (GradleContextualExecuter.isNotConfigCache()) {
            return
        }

        if (!isEnabledSpec(annotation, feature)) {
            return
        }

        if (annotation.skip() != DO_NOT_SKIP) {
            feature.skipped = true
            return
        }

        toBeFixedSpecInterceptor.intercept(feature, annotation.iterationMatchers())
    }

    private static boolean isEnabledSpec(ToBeFixedForConfigurationCache annotation, FeatureInfo feature) {
        isEnabledBottomSpec(annotation.bottomSpecs(), { it == feature.spec.bottomSpec.name })
    }

    static boolean isEnabledBottomSpec(String[] bottomSpecs, Predicate<String> specNamePredicate) {
        bottomSpecs.length == 0 || bottomSpecs.any { specNamePredicate.test(it) }
    }
}
