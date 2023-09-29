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

package gradlebuild.instrumentation.extensions

import gradlebuild.basics.classanalysis.Attributes
import gradlebuild.instrumentation.transforms.InstrumentationMetadataTransform.Companion.INSTRUMENTATION_METADATA
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty


abstract class InstrumentationMetadataExtension(private val configurations: ConfigurationContainer) {

    companion object {
        const val INSTRUMENTED_SUPER_TYPES_MERGE_TASK = "mergeInstrumentedSuperTypes"
        const val UPGRADED_PROPERTIES_MERGE_TASK = "mergeUpgradedProperties"
        const val INSTRUMENTED_METADATA_EXTENSION = "instrumentationMetadata"
    }

    abstract val classpathToInspect: ConfigurableFileCollection
    abstract val superTypesOutputFile: RegularFileProperty
    abstract val upgradedPropertiesFile: RegularFileProperty

    @Suppress("MemberVisibilityCanBePrivate")
    fun createInstrumentationMetadataViewOf(other: Configuration): FileCollection {
        val runtimeInstrumentationMetadata = configurations.create("${other.name}InstrumentationMetadata") {
            attributes.attribute(Attributes.artifactType, INSTRUMENTATION_METADATA)
            isCanBeResolved = true
            isCanBeConsumed = false
            extendsFrom(other)
        }
        return runtimeInstrumentationMetadata.toProjectsOnlyView()
    }

    fun Configuration.toInstrumentationMetadataView(): FileCollection {
        return createInstrumentationMetadataViewOf(this)
    }

    private
    fun Configuration.toProjectsOnlyView() = incoming.artifactView {
        componentFilter { id -> id is ProjectComponentIdentifier && id.isNotBuildLogic }
    }.files

    // TODO Figure out how to do this properly and remove this hack!
    private
    val ProjectComponentIdentifier.isNotBuildLogic: Boolean
        get() = !buildTreePath.contains("build-logic")
}
