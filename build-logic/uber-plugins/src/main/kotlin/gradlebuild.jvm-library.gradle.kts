/*
 * Copyright 2024 the original author or authors.
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

import com.autonomousapps.DependencyAnalysisSubExtension
import gradlebuild.attributes.ExternallyAvailableLibraryAttribute

plugins {
    id("java-library")
    id("gradlebuild.module-identity")
    id("gradlebuild.dependency-modules")
    id("gradlebuild.repositories")
    id("gradlebuild.minify")
    id("gradlebuild.reproducible-archives")
    id("gradlebuild.unittest-and-compile")
    id("gradlebuild.test-fixtures")
    id("gradlebuild.distribution-testing")
    id("gradlebuild.incubation-report")
    id("gradlebuild.strict-compile")
    id("gradlebuild.code-quality")
    id("gradlebuild.arch-test")
    id("gradlebuild.integration-tests")
    id("gradlebuild.cross-version-tests")
    id("gradlebuild.ci-lifecycle")
    id("gradlebuild.ci-reporting") // CI: Prepare reports to be uploaded to TeamCity
    id("gradlebuild.configure-ci-artifacts") // CI: Prepare reports to be uploaded to TeamCity
    id("com.autonomousapps.dependency-analysis") // Auditing dependencies to find unused libraries
}

ensurePublishedLibrariesOnlyDependOnPublishedLibraries()

configure<DependencyAnalysisSubExtension> {
    issues {
        onAny {
            severity("fail")
        }

        onUnusedAnnotationProcessors {
            // Ignore check for internal-instrumentation-processor, since we apply
            // it to all distribution.api-java projects but projects might not have any upgrade
            exclude(":internal-instrumentation-processor")
        }

        ignoreSourceSet("archTest", "crossVersionTest", "docsTest", "integTest", "jmh", "peformanceTest", "smokeTest", "testInterceptors", "testFixtures", "smokeIdeTest")
    }
}

class ExternallyAvailableAttributeCompatibilityRule : AttributeCompatibilityRule<Boolean> {
    override fun execute(details: CompatibilityCheckDetails<Boolean>) {
        if (details.consumerValue != true) {
            // We don't care if the producer is published or not.
            details.compatible()
        } else if (details.producerValue == null) {
            // We assume all gradle JVM library variants define this attribute
            // Therefore, any producers which do not are not from the Gradle build and are external libraries.
            details.compatible()
        } else if (details.producerValue == true) {
            // This is a published Gradle library
            details.compatible()
        } else {
            // This is an unpublished Gradle library
            details.incompatible()
        }
    }
}

/**
 * TODO: Verification of externally available libraries should probably be handled natively
 *       by the Gradle publishing plugins. Gradle should refuse to publish a project with a
 *       dependency on another project, unless the other project is also configured for publishing.
 */
fun ensurePublishedLibrariesOnlyDependOnPublishedLibraries() {
    // If our library is externally available, declare it as such.
    listOf(configurations.apiElements, configurations.runtimeElements).configureEach {
        attributes {
            attributeProvider(ExternallyAvailableLibraryAttribute.attribute, moduleIdentity.published)
        }
    }

    // If our library is externally available, require that our dependencies are also externally available.
    listOf(configurations.runtimeClasspath, configurations.compileClasspath).configureEach {
        attributes {
            attributeProvider(ExternallyAvailableLibraryAttribute.attribute, moduleIdentity.published)
        }
    }

    dependencies {
        attributesSchema {
            attribute(ExternallyAvailableLibraryAttribute.attribute) {
                compatibilityRules.add(ExternallyAvailableAttributeCompatibilityRule::class.java)
            }
        }
    }
}

fun <T> Iterable<NamedDomainObjectProvider<T>>.configureEach(action: T.() -> Unit) {
    forEach { provider ->
        provider.configure {
            action()
        }
    }
}
