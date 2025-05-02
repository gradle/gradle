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
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

import static org.gradle.integtests.fixtures.ToBeFixedForConfigurationCacheExtension.isEnabledBottomSpec
import static ToBeFixedSpecInterceptor.iterationMatches

/**
 * JUnit Rule supporting the {@link ToBeFixedForConfigurationCache} annotation.
 */
class ToBeFixedForConfigurationCacheRule implements TestRule {

    @Override
    Statement apply(Statement base, Description description) {
        def annotation = description.getAnnotation(ToBeFixedForConfigurationCache.class)
        if (GradleContextualExecuter.isNotConfigCache() || annotation == null) {
            return base
        }
        def enabledBottomSpec = isEnabledBottomSpec(annotation.bottomSpecs(), { description.className.endsWith(".$it") })
        def enabledIteration = iterationMatches(annotation.iterationMatchers(), description.methodName)
        if (enabledBottomSpec && enabledIteration) {
            ToBeFixedForConfigurationCache.Skip skip = annotation.skip()
            if (skip == ToBeFixedForConfigurationCache.Skip.DO_NOT_SKIP) {
                return new ExpectingFailureRuleStatement(base, "Configuration Cache")
            } else {
                return new UnsupportedWithConfigurationCacheRule.SkippingRuleStatement(base)
            }
        }
        return base
    }
}
