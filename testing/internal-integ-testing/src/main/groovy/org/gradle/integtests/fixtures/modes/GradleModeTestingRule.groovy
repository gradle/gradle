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

import org.gradle.test.fixtures.file.AbstractTestDirectoryProvider
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

import java.lang.annotation.Annotation
import java.util.function.Function

import static org.gradle.integtests.fixtures.modes.GradleModeTesting.CONFIGURATION_CACHE
import static org.gradle.integtests.fixtures.modes.GradleModeTesting.ISOLATED_PROJECTS
import static org.gradle.integtests.fixtures.modes.GradleModeTesting.bottomSpecMatches
import static org.gradle.integtests.fixtures.modes.GradleModeTesting.iterationMatches
import static org.gradle.integtests.fixtures.modes.GradleModeTesting.unsupportedSkipReason
import static org.junit.Assume.assumeTrue

/**
 * JUnit Rule supporting Gradle mode testing.
 */
class GradleModeTestingRule implements TestRule {

    @Override
    Statement apply(Statement base, Description description) {
        def next = base
        next = apply(next, description, CONFIGURATION_CACHE, ToBeFixedForConfigurationCache.class, it -> it.skipBecause())
        next = apply(next, description, CONFIGURATION_CACHE, UnsupportedWithConfigurationCache.class, it -> unsupportedSkipReason(it.because()))
        next = apply(next, description, ISOLATED_PROJECTS, ToBeFixedForIsolatedProjects.class, it -> it.skipBecause())
        next = apply(next, description, ISOLATED_PROJECTS, UnsupportedWithIsolatedProjects.class, it -> unsupportedSkipReason(it.because()))
        next
    }

    // If the annotation is applied and applicable, then either skip the test (if reason provided) or expect it to fail
    <A extends Annotation> Statement apply(
        Statement base,
        Description description,
        GradleModeTesting gradleMode,
        Class<A> annClass,
        Function<A, String> getSkipReason
    ) {
        def annotation = description.getAnnotation(annClass)
        if (annotation == null || !gradleMode.active) {
            return base
        }
        def enabledBottomSpec = bottomSpecMatches(annotation.bottomSpecs() as String[]) { description.className.endsWith(".$it") }
        def enabledIteration = iterationMatches(annotation.iterationMatchers() as String[], description.methodName)
        if (enabledBottomSpec && enabledIteration) {
            def skipReason = getSkipReason.apply(annotation)
            if (!skipReason) {
                return new ExpectFailure(base, gradleMode.displayName())
            } else {
                return new Skip(gradleMode.displayName(), skipReason)
            }
        }
        return base
    }

    static class Skip extends Statement {

        private final String gradleMode
        private final String reason

        Skip(String gradleMode, String reason) {
            this.gradleMode = gradleMode
            this.reason = reason
        }

        @Override
        void evaluate() throws Throwable {
            assumeTrue("Skipping for '$gradleMode' mode: $reason", false)
        }
    }

    static class ExpectFailure extends Statement {

        private final Statement next
        private final String gradleMode

        ExpectFailure(Statement next, String gradleMode) {
            this.next = next
            this.gradleMode = gradleMode
        }

        @Override
        void evaluate() throws Throwable {
            try {
                next.evaluate()
                throw new ToBeFixedUnexpectedSuccessException(gradleMode)
            } catch (ToBeFixedUnexpectedSuccessException ex) {
                throw ex
            } catch (Throwable ex) {
                System.err.println("Failed with '$gradleMode' mode as expected:")
                ex.printStackTrace()

                // TODO: it is not guaranteed that the cleaning statement is the next
                if (next instanceof AbstractTestDirectoryProvider.TestDirectoryCleaningStatement) {
                    next.cleanup()
                }
            }
        }
    }
}
