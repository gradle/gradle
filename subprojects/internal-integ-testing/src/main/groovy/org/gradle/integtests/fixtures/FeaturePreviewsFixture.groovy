/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.internal.FeaturePreviews.Feature

class FeaturePreviewsFixture {

    static def activeFeatures() {
        EnumSet.of(Feature.GRADLE_METADATA, Feature.INCREMENTAL_ARTIFACT_TRANSFORMATIONS)
    }

    static def inactiveFeatures() {
        def features = EnumSet.allOf(Feature.class)
        features.removeAll(activeFeatures())
        features
    }

    static void enableGradleMetadata(File settings) {
        settings << """
enableFeaturePreview("GRADLE_METADATA")
"""
    }

    static void enableIncrementalArtifactTransformations(File settings) {
        settings << """
enableFeaturePreview("INCREMENTAL_ARTIFACT_TRANSFORMATIONS")
"""
    }
}
