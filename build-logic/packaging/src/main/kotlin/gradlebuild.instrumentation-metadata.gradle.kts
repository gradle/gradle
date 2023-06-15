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

import gradlebuild.basics.classanalysis.Attributes
import gradlebuild.instrumentation.tasks.FindInstrumentedSuperTypesTask
import gradlebuild.instrumentation.transforms.CollectDirectClassSuperTypesTransform
import gradlebuild.instrumentation.transforms.CollectDirectClassSuperTypesTransform.Companion.INSTRUMENTATION_METADATA

/**
 * A plugin that configures tasks to generate metadata that is needed for code instrumentation.
 */
dependencies {
    registerTransform(CollectDirectClassSuperTypesTransform::class) {
        from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JVM_CLASS_DIRECTORY)
        to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, INSTRUMENTATION_METADATA)
    }
}

val runtimeInstrumentationMetadata = configurations.create("runtimeInstrumentationMetadata") {
    attributes.attribute(Attributes.artifactType, INSTRUMENTATION_METADATA)
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations.getByName("runtimeClasspath"))
}

tasks.register<FindInstrumentedSuperTypesTask>("findInstrumentedSuperTypes") {
    instrumentationMetadataDirs = runtimeInstrumentationMetadata.projectsOnlyView()
    instrumentedSuperTypes = layout.buildDirectory.file("instrumentation/instrumented-super-types.properties")
}

fun Configuration.projectsOnlyView() = incoming.artifactView {
    componentFilter { id -> id is ProjectComponentIdentifier }
    lenient(true)
}.files
