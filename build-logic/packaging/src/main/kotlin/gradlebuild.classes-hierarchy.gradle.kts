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
import gradlebuild.shade.ArtifactTypes
import gradlebuild.shade.tasks.InstrumentationMergeSuperTypesTask
import gradlebuild.shade.transforms.ClassSuperTypesCollector

dependencies {
    registerTransform(ClassSuperTypesCollector::class) {
        from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JVM_CLASS_DIRECTORY)
        to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypes.classHierarchy)
        parameters {
            rootDir = project.rootDir.absolutePath
        }
    }
}

val configuration = configurations.create("classesHierarchy") {
    attributes.attribute(Attributes.artifactType, ArtifactTypes.classHierarchy)
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations.getByName("runtimeClasspath"))
}

tasks.register<InstrumentationMergeSuperTypesTask>("classesHierarchy") {
    superTypesFiles.from(configuration.artifactView())
    instrumentedClasses = listOf("org/gradle/api/Task")
    instrumentedSuperTypes = layout.buildDirectory.file("instrumentation/classes-super-types.properties")
}

fun Configuration.artifactView() = incoming.artifactView {
    componentFilter { id -> id is ProjectComponentIdentifier }
}.files
