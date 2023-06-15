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
import gradlebuild.instrumentation.transforms.CollectDirectClassSuperTypesTransform.Companion.DIRECT_SUPER_TYPES

/**
 * A plugin that configures tasks to generate metadata that is needed for code instrumentation.
 */
dependencies {
    registerTransform(CollectDirectClassSuperTypesTransform::class) {
        from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JVM_CLASS_DIRECTORY)
        to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, DIRECT_SUPER_TYPES)
    }
}

val runtimeDirectSuperTypes = configurations.create("runtimeDirectSuperTypes") {
    attributes.attribute(Attributes.artifactType, DIRECT_SUPER_TYPES)
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations.getByName("runtimeClasspath"))
}

val runtimeProjectResources = configurations.create("runtimeProjectResources") {
    attributes.attribute(Attributes.artifactType, ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY)
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations.getByName("runtimeClasspath"))
}


tasks.register<FindInstrumentedSuperTypesTask>("findInstrumentedSuperTypes") {
    directSuperTypesFiles = runtimeDirectSuperTypes.projectsOnlyView()
    projectResourceDirs = files()
    instrumentedSuperTypes = layout.buildDirectory.file("instrumentation/instrumented-super-types.properties")
}

fun Configuration.projectsOnlyView() = incoming.artifactView {
    componentFilter { id -> id is ProjectComponentIdentifier }
    lenient(true)
}.files
