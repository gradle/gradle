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

import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.tasks.GenerateModuleMetadata

/**
 * Assembles this module's slice of the Maven repository EMBEDDED in the Gradle distribution
 * (`repo/` in the distribution image): the ordinary `gradleDistribution` publication's POM and
 * Gradle Module Metadata plus the real jars, at the full timestamped distribution version —
 * deliberately NOT the normalized base version, because `versionMapping { fromResolutionResult() }`
 * writes resolved (timestamped) versions into the POM's dependency entries, and because the runtime
 * consumes the repo keyed by `GradleVersion.current()`, which IS the timestamped version.
 *
 * The Maven layout is produced by a plain [Sync] over the publication's generation-task outputs
 * rather than by `PublishToMavenRepository`, ON PURPOSE: publish tasks declare themselves
 * incompatible with the configuration cache, and these slices are `builtBy` the distribution
 * image — a publish task in that graph would cost every integration-test invocation its
 * configuration cache. The jar/pom/module files land under the exact names the module metadata
 * declares (artifactId-fullVersion), so resolution sees precisely what a real publish would have
 * written; checksum/`maven-metadata.xml` side files are not needed for exact-version resolution
 * from a `file://` repository.
 *
 * The per-project repo slice is exposed through the `distributionRepositoryElements` variant
 * (category `gradle-distribution-repository`); the distribution build resolves all slices and
 * merges them under `repo/` (distinct `<group-path>/<module>/` trees, so a plain copy merge is
 * safe). Any published module can opt in — the facility is not XDCL-specific, and is the intended
 * home for distribution components that should participate in ordinary dependency resolution
 * rather than (only) the module registry.
 */

plugins {
    id("gradlebuild.publish-public-libraries")
}

val distributionRepository: Provider<Directory> = layout.buildDirectory.dir("distribution-repository")

// Identity values resolved once at configuration time (the same eager reads
// gradlebuild.publish-public-libraries makes). The renames below use the STRING overload, not a
// lambda: a lambda here would capture the script instance, which the configuration cache rejects.
val artifactId = gradleModule.identity.baseName.get()
val fullVersion = gradleModule.identity.version.get().version

val assembleDistributionRepository = tasks.register<Sync>("assembleDistributionRepository") {
    description = "Assembles this module's slice of the distribution's embedded Maven repository (repo/)"
    into(distributionRepository)
    into("${project.group.toString().replace('.', '/')}/$artifactId/$fullVersion") {
        from(tasks.named<GenerateMavenPom>("generatePomFileForGradleDistributionPublication")) {
            rename(".*", "$artifactId-$fullVersion.pom")
        }
        from(tasks.named<GenerateModuleMetadata>("generateMetadataFileForGradleDistributionPublication")) {
            rename(".*", "$artifactId-$fullVersion.module")
        }
        // Jar FILE names use the base version (see gradlebuild.module-identity), while the
        // publication — and therefore the module metadata — names them by the full version.
        from(tasks.named("jar")) {
            rename(".*", "$artifactId-$fullVersion.jar")
        }
        from(tasks.named("sourcesJar")) {
            rename(".*", "$artifactId-$fullVersion-sources.jar")
        }
        from(tasks.named("javadocJar")) {
            rename(".*", "$artifactId-$fullVersion-javadoc.jar")
        }
    }
}

// Consumed by the distribution projects to assemble `repo/`.
configurations.create("distributionRepositoryElements") {
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named("gradle-distribution-repository"))
    }
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(distributionRepository) {
        builtBy(assembleDistributionRepository)
    }
}
