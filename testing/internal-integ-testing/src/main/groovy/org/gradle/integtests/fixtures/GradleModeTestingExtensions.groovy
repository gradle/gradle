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

package org.gradle.integtests.fixtures

import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo

/**
 * Spock extensions that gate Spock specs and features by the active {@link GradleModeTesting}.
 */
final class GradleModeTestingExtensions {

    private GradleModeTestingExtensions() {}

    static class ToBeFixedForCC implements IAnnotationDrivenExtension<ToBeFixedForConfigurationCache> {

        private final GradleModeTestingExtension<ToBeFixedForConfigurationCache> adapter = new GradleModeTestingExtension<>(
            new GradleModeTestingPolicy.ToBeFixedForCC(),
            new ToBeFixedSpecInterceptor(GradleModeTesting.CONFIGURATION_CACHE.displayName())
        )

        @Override
        void visitSpecAnnotation(ToBeFixedForConfigurationCache annotation, SpecInfo spec) {
            adapter.applyAtSpec(annotation, spec)
        }

        @Override
        void visitFeatureAnnotation(ToBeFixedForConfigurationCache annotation, FeatureInfo feature) {
            adapter.applyAtFeature(annotation, feature)
        }
    }

    static class ToBeFixedForIP implements IAnnotationDrivenExtension<ToBeFixedForIsolatedProjects> {

        private final GradleModeTestingExtension<ToBeFixedForIsolatedProjects> adapter = new GradleModeTestingExtension<>(
            new GradleModeTestingPolicy.ToBeFixedForIP(),
            new ToBeFixedSpecInterceptor(GradleModeTesting.ISOLATED_PROJECTS.displayName())
        )

        @Override
        void visitSpecAnnotation(ToBeFixedForIsolatedProjects annotation, SpecInfo spec) {
            adapter.applyAtSpec(annotation, spec)
        }

        @Override
        void visitFeatureAnnotation(ToBeFixedForIsolatedProjects annotation, FeatureInfo feature) {
            adapter.applyAtFeature(annotation, feature)
        }
    }

    static class UnsupportedWithCC implements IAnnotationDrivenExtension<UnsupportedWithConfigurationCache> {

        private final GradleModeTestingExtension<UnsupportedWithConfigurationCache> adapter = new GradleModeTestingExtension<>(
            new GradleModeTestingPolicy.UnsupportedWithCC(), null
        )

        @Override
        void visitSpecAnnotation(UnsupportedWithConfigurationCache annotation, SpecInfo spec) {
            adapter.applyAtSpec(annotation, spec)
        }

        @Override
        void visitFeatureAnnotation(UnsupportedWithConfigurationCache annotation, FeatureInfo feature) {
            adapter.applyAtFeature(annotation, feature)
        }
    }

    static class UnsupportedWithIP implements IAnnotationDrivenExtension<UnsupportedWithIsolatedProjects> {

        private final GradleModeTestingExtension<UnsupportedWithIsolatedProjects> adapter = new GradleModeTestingExtension<>(
            new GradleModeTestingPolicy.UnsupportedWithIP(), null
        )

        @Override
        void visitSpecAnnotation(UnsupportedWithIsolatedProjects annotation, SpecInfo spec) {
            adapter.applyAtSpec(annotation, spec)
        }

        @Override
        void visitFeatureAnnotation(UnsupportedWithIsolatedProjects annotation, FeatureInfo feature) {
            adapter.applyAtFeature(annotation, feature)
        }
    }
}
