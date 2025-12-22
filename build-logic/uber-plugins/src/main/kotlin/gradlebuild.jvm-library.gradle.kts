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
    id("gradlebuild.no-module-annotation")
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

        onDuplicateClassWarnings {
            severity("fail")
        }

        ignoreSourceSet("archTest", "crossVersionTest", "integTest", "jmh", "testFixtures")

    }
}

class ExternallyAvailableAttributeCompatibilityRule : AttributeCompatibilityRule<Boolean> {
    override fun execute(details: CompatibilityCheckDetails<Boolean>) {
        val consumerIsPublished = details.consumerValue == true

        // We assume all gradle JVM library variants define this attribute,
        // so the absence of the attribute (producerValue == null) means the consumer is
        // not a Gradle build variant and therefore is assumed external.
        val producerIsPublished = details.producerValue != false

        if (!consumerIsPublished || producerIsPublished) {
            // If the consumer is not published, we don't care if the producer is published or not.
            // Or, if it is published, the producer must be published.
            details.compatible()
        } else {
            // Otherwise, the consumer is published but the producer is not, which is not permitted.
            details.incompatible()
        }
    }
}

// TODO #35531: Verification of externally available libraries should be handled natively
//       by the Gradle publishing plugins. Gradle should refuse to publish a project with a
//       dependency on another project, unless the other project is also configured for publishing.
fun ensurePublishedLibrariesOnlyDependOnPublishedLibraries() {
    // If our library is externally available, declare it as such.
    listOf(configurations.apiElements, configurations.runtimeElements).configureEach {
        attributes {
            attributeProvider(ExternallyAvailableLibraryAttribute.attribute, gradleModule.published)
        }
    }

    // If our library is externally available, require that our dependencies are also externally available.
    // TODO: We should also probably check the compile classpath
    configurations.runtimeClasspath {
        attributes {
            attributeProvider(ExternallyAvailableLibraryAttribute.attribute, gradleModule.published)
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

fun <T: Any> Iterable<NamedDomainObjectProvider<T>>.configureEach(action: T.() -> Unit) {
    forEach { provider ->
        provider.configure {
            action()
        }
    }
}
