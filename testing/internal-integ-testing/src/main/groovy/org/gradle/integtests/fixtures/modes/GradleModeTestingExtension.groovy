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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.reflect.ClassInspector
import org.gradle.test.fixtures.ResettableExpectations
import org.opentest4j.TestAbortedException
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecElementInfo
import org.spockframework.runtime.model.SpecInfo

import java.lang.annotation.Annotation
import java.lang.reflect.Field

import static org.gradle.integtests.fixtures.modes.GradleModeTesting.CONFIGURATION_CACHE
import static org.gradle.integtests.fixtures.modes.GradleModeTesting.ISOLATED_PROJECTS
import static org.gradle.integtests.fixtures.modes.GradleModeTesting.bottomSpecMatches
import static org.gradle.integtests.fixtures.modes.GradleModeTesting.iterationAlwaysMatches
import static org.gradle.integtests.fixtures.modes.GradleModeTesting.iterationMatches
import static org.gradle.integtests.fixtures.modes.GradleModeTesting.unsupportedSkipReason

/**
 * Spock extensions that react to Gradle mode-testing annotations.
 */
abstract class GradleModeTestingExtension<A extends Annotation> implements IAnnotationDrivenExtension<A> {

    @Override
    void visitSpecAnnotation(A annotation, SpecInfo spec) {
        visitSpecOrFeature(annotation, spec)
    }

    @Override
    void visitFeatureAnnotation(A annotation, FeatureInfo feature) {
        visitSpecOrFeature(annotation, feature)
    }

    protected abstract void visitSpecOrFeature(A annotation, SpecElementInfo specOrFeature)

    protected static void visitSpecOrFeature(
        GradleModeTesting gradleMode,
        SpecElementInfo specOrFeature,
        String[] bottomSpecs,
        String[] iterationMatchers,
        String skipReason
    ) {
        if (!(gradleMode.active && bottomSpecMatches(bottomSpecs) { bottomSpecOf(specOrFeature).bottomSpec.name == it })) {
            return
        }

        def modeName = gradleMode.displayName()
        if (skipReason) {
            if (iterationAlwaysMatches(iterationMatchers)) {
                specOrFeature.skip(skipReason)
            } else {
                forEachFeature(specOrFeature) { feature ->
                    feature.featureMethod.addInterceptor { IMethodInvocation invocation ->
                        if (iterationMatches(iterationMatchers, invocation.iteration.displayName)) {
                            throw new TestAbortedException("Skipping for '$modeName' mode, because: $skipReason")
                        }
                        invocation.proceed()
                    }
                }
            }
        } else {
            forEachFeature(specOrFeature) { feature ->
                feature.featureMethod.addInterceptor { IMethodInvocation invocation ->
                    if (iterationMatches(iterationMatchers, invocation.iteration.displayName)) {
                        if (failsAsExpected(invocation, modeName)) {
                            throw new TestAbortedException("Failed as expected.")
                        }
                        throw new ToBeFixedUnexpectedSuccessException(modeName)
                    }
                    invocation.proceed()
                }
            }
        }
    }

    private static SpecInfo bottomSpecOf(SpecElementInfo specOrFeature) {
        specOrFeature instanceof SpecInfo ? (SpecInfo) specOrFeature : ((FeatureInfo) specOrFeature).parent
    }

    private static void forEachFeature(SpecElementInfo specOrFeature, Closure<?> action) {
        if (specOrFeature instanceof SpecInfo) {
            specOrFeature.features.each { action(it) }
        } else {
            action((FeatureInfo) specOrFeature)
        }
    }

    private static boolean failsAsExpected(IMethodInvocation invocation, String gradleMode) {
        try {
            invocation.proceed()
        } catch (Throwable ex) {
            expectedFailure(ex, gradleMode)
            ignoreCleanupAssertionsOf(invocation)
            return true
        }
        // Trigger validation failures early so they can still fail the test the usual way
        try {
            allResettableExpectationsOf(invocation.instance).forEach { it.resetExpectations() }
        } catch (Throwable ex) {
            expectedFailure(ex, gradleMode)
            ignoreCleanupAssertionsOf(invocation)
            return true
        }
        return false
    }

    private static void expectedFailure(Throwable ex, String gradleMode) {
        System.err.println("Failed with '$gradleMode' mode as expected:")
        ex.printStackTrace()
    }

    private static ignoreCleanupAssertionsOf(IMethodInvocation invocation) {
        def instance = invocation.instance
        if (instance instanceof AbstractIntegrationSpec) {
            instance.ignoreCleanupAssertions()
        }
        allResettableExpectationsOf(instance).forEach { expectations ->
            try {
                expectations.resetExpectations()
            } catch (Throwable error) {
                error.printStackTrace()
            }
        }
    }

    private static List<ResettableExpectations> allResettableExpectationsOf(instance) {
        allInstanceFieldsOf(instance).findResults { field ->
            try {
                def fieldValue = field.tap { accessible = true }.get(instance)
                fieldValue instanceof ResettableExpectations ? fieldValue : null
            } catch (Exception ignored) {
                null
            }
        }
    }

    private static Collection<Field> allInstanceFieldsOf(instance) {
        ClassInspector.inspect(instance.getClass()).instanceFields
    }

    static class ToBeFixedForCC extends GradleModeTestingExtension<ToBeFixedForConfigurationCache> {
        @Override
        protected void visitSpecOrFeature(ToBeFixedForConfigurationCache a, SpecElementInfo specOrFeature) {
            visitSpecOrFeature(CONFIGURATION_CACHE, specOrFeature, a.bottomSpecs(), a.iterationMatchers(), a.skipBecause())
        }
    }

    static class UnsupportedWithCC extends GradleModeTestingExtension<UnsupportedWithConfigurationCache> {
        @Override
        protected void visitSpecOrFeature(UnsupportedWithConfigurationCache a, SpecElementInfo specOrFeature) {
            visitSpecOrFeature(CONFIGURATION_CACHE, specOrFeature, a.bottomSpecs(), a.iterationMatchers(), unsupportedSkipReason(a.because()))
        }
    }

    static class ToBeFixedForIP extends GradleModeTestingExtension<ToBeFixedForIsolatedProjects> {
        @Override
        protected void visitSpecOrFeature(ToBeFixedForIsolatedProjects a, SpecElementInfo specOrFeature) {
            visitSpecOrFeature(ISOLATED_PROJECTS, specOrFeature, a.bottomSpecs(), a.iterationMatchers(), a.skipBecause())
        }
    }

    static class UnsupportedWithIP extends GradleModeTestingExtension<UnsupportedWithIsolatedProjects> {
        @Override
        protected void visitSpecOrFeature(UnsupportedWithIsolatedProjects a, SpecElementInfo specOrFeature) {
            visitSpecOrFeature(ISOLATED_PROJECTS, specOrFeature, a.bottomSpecs(), a.iterationMatchers(), unsupportedSkipReason(a.because()))
        }
    }
}
