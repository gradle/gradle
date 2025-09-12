/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.test.fixtures

import org.gradle.api.logging.configuration.WarningMode
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.util.TestUtil
import org.junit.function.ThrowingRunnable
import org.spockframework.runtime.extension.ExtensionAnnotation
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.model.FeatureInfo

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.lang.reflect.Field

/**
 * Initializes the deprecation logger to avoid warnings about it not being initialized in <strong>unit</strong> tests
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ExtensionAnnotation(ExpectDeprecationExtension.class)
@interface ExpectDeprecation {
    String value()
}

class ExpectDeprecationExtension implements IAnnotationDrivenExtension<ExpectDeprecation> {
    // Deprecation logger should not be deinitialized in production code, so using reflection to reset it here
    private static final Field INIT_FIELD = DeprecationLogger.class.getDeclaredField("initialized").tap {
        it.setAccessible(true)
    }

    static void intercept(String expectedMessage, ThrowingRunnable proceed) {
        assert expectedMessage != null && expectedMessage.size() > 10: "Please provide the expected deprecation message"

        def problems = TestUtil.problemsService()
        DeprecationLogger.init(WarningMode.All, null, problems, NoOpProblemDiagnosticsFactory.EMPTY_STREAM)

        try {
            proceed()
        } finally {
            INIT_FIELD.set(DeprecationLogger, false)
        }

        problems.assertHasDeprecation(expectedMessage)
    }

    @Override
    void visitFeatureAnnotation(ExpectDeprecation annotation, FeatureInfo feature) {
        feature.featureMethod.addInterceptor { invocation ->
            intercept(annotation.value(), invocation::proceed)
        }
    }
}
