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

package org.gradle.internal.serialize.codecs.dm

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.LenientConfiguration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler
import org.gradle.api.artifacts.query.ArtifactResolutionQuery
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.result.ArtifactResolutionResult
import org.gradle.api.artifacts.result.ArtifactResult
import org.gradle.api.artifacts.result.ComponentArtifactsResult
import org.gradle.api.artifacts.result.UnresolvedComponentResult
import org.gradle.api.attributes.AttributeMatchingStrategy
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.CompatibilityRuleChain
import org.gradle.api.attributes.DisambiguationRuleChain
import org.gradle.internal.serialize.graph.codecs.BindingsBuilder
import org.gradle.internal.serialize.graph.unsupported

fun BindingsBuilder.unsupportedDependencyManagementTypes() {

    bind(unsupported<ArtifactRepository>())
    bind(unsupported<ArtifactResolutionQuery>())
    bind(unsupported<ArtifactResolutionResult>())
    bind(unsupported<ArtifactResult>())
    bind(unsupported<AttributeMatchingStrategy<*>>())
    bind(unsupported<AttributesSchema>())
    bind(unsupported<CompatibilityRuleChain<*>>())
    bind(unsupported<ComponentArtifactsResult>())
    bind(unsupported<ComponentModuleMetadataHandler>())
    bind(unsupported<Dependency>())
    bind(unsupported<DisambiguationRuleChain<*>>())
    bind(unsupported<LenientConfiguration>())
    bind(unsupported<ResolvedArtifact>())
    bind(unsupported<ResolvedConfiguration>())
    bind(unsupported<ResolvedDependency>())
    bind(unsupported<UnresolvedComponentResult>())

}
