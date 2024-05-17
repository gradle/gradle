/*
 * Copyright 2023 the original author or authors.
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

import gradlebuild.instrumentation.extensions.InstrumentationMetadataExtension
import gradlebuild.instrumentation.extensions.InstrumentationMetadataExtension.Companion.INSTRUMENTED_METADATA_EXTENSION
import gradlebuild.instrumentation.extensions.InstrumentationMetadataExtension.Companion.INSTRUMENTED_SUPER_TYPES_MERGE_TASK
import gradlebuild.instrumentation.extensions.InstrumentationMetadataExtension.Companion.UPGRADED_PROPERTIES_MERGE_TASK
import gradlebuild.instrumentation.tasks.InstrumentedSuperTypesMergeTask
import gradlebuild.instrumentation.tasks.UpgradedPropertiesMergeTask
import gradlebuild.instrumentation.transforms.InstrumentationMetadataTransform
import gradlebuild.instrumentation.transforms.InstrumentationMetadataTransform.Companion.INSTRUMENTATION_METADATA
import org.gradle.api.internal.GradleInternal

/**
 * A plugin that configures tasks and transforms to generate metadata that is needed for code instrumentation.
 */
dependencies {
    registerTransform(InstrumentationMetadataTransform::class) {
        from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JVM_CLASS_DIRECTORY)
        to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, INSTRUMENTATION_METADATA)
    }
}

val extension = extensions.create<InstrumentationMetadataExtension>(
    INSTRUMENTED_METADATA_EXTENSION,
    (gradle as GradleInternal).owner.buildIdentifier,
)

tasks.register<InstrumentedSuperTypesMergeTask>(INSTRUMENTED_SUPER_TYPES_MERGE_TASK) {
    instrumentationMetadataDirs = extension.classpathToInspect
    instrumentedSuperTypes = extension.superTypesOutputFile
}

tasks.register<UpgradedPropertiesMergeTask>(UPGRADED_PROPERTIES_MERGE_TASK) {
    instrumentationMetadataDirs = extension.classpathToInspect
    upgradedProperties = extension.upgradedPropertiesFile
}
