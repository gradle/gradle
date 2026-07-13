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

package gradlebuild.basics

import org.gradle.api.attributes.Attribute


/**
 * Marks variants of a Gradle distribution collector project.
 * <p>
 * The standard runtime variant of a distribution collector (e.g. `:distributions-full`) publishes
 * every packaged artifact — including `runtime-api-info.jar`.  This means that tasks that depend
 * on it rely on the tasks that build it, which scan the entire codebase's classes and sources.
 * Variants advertising a value can here expose only a subset, skipping the packaging metadata
 * pipeline for consumers that don't need it.
 * <p>
 * Consumers targeting a narrower scope declare a value in their request; the standard runtime
 * variant does not declare this attribute at all.
 */
enum class DistributionArtifactScope {
    /**
     * Only the transitively-resolved module JARs (via the collector's `coreRuntimeOnly` and
     * `pluginsRuntimeOnly` buckets) plus the Kotlin DSL extensions JAR.
     * <p>
     * Notably excludes `runtime-api-info.jar` and any other packaging-derived artifacts. Consumed
     * by `:architecture-test` to keep the packaging metadata pipeline off the ArchUnit scan's critical
     * path and allow for faster `sanityCheck` execution by shortening the critical path to start ArchUnit.
     */
    RUNTIME_ONLY;

    companion object {
        val attribute: Attribute<DistributionArtifactScope> = Attribute.of(DistributionArtifactScope::class.java)
    }
}
