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
 * A distribution collector (e.g. `:distributions-full`) exposes multiple consumable runtime
 * variants that differ in how much of the packaging pipeline they include.  This attribute
 * distinguishes them so consumers can opt into a narrower artifact set.
 * <p>
 * Every runtime variant of a collector declares a value.  Consumers that don't declare a value
 * fall back to `STANDARD` via the disambiguation rule registered in the packaging plugin.
 */
enum class DistributionArtifactScope {
    /**
     * The full runtime artifact set — every packaged JAR the collector publishes, including
     * `runtime-api-info.jar` and its metadata-derivation task subtree (relocated package list,
     * plugins manifest, instrumented super-types merge, upgraded properties merge, DSL meta,
     * api mapping, default imports).
     * <p>
     * Advertised by the standard `runtime` variant of each collector; also selected by consumers
     * that don't declare a `DistributionArtifactScope` value in their request.
     */
    STANDARD,

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
