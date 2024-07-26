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

package org.gradle.plugin.devel.variants

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByAttributesException

class TargetJVMVersionOnLibraryTooNewFailureDescriberIntegrationTest extends AbstractIntegrationSpec {
    Integer currentJava = Integer.valueOf(JavaVersion.current().majorVersion)
    Integer tooHighJava = currentJava + 1

    def 'JVM version too low on non-plugin dependency uses standard error message'() {
        given:
        def producer = file('producer')
        def consumer = file('consumer')

        file('settings.gradle') << """
            include 'producer', 'consumer'
        """

        producer.file('build.gradle') << """
            plugins {
                id('java-library')
            }

            java {
                sourceCompatibility = $tooHighJava
                targetCompatibility = $tooHighJava
            }
        """

        consumer.file('build.gradle') << """
            plugins {
                id('java-library')
            }

            dependencies {
                implementation project(':producer')
            }
        """

        when:
        fails ':consumer:build', "--stacktrace"

        then:
        failure.assertHasErrorOutput("""> Could not resolve all dependencies for configuration ':consumer:compileClasspath'.
   > Could not resolve project :producer.
     Required by:
         project :consumer
      > Dependency resolution is looking for a library compatible with JVM runtime version $currentJava, but 'project :producer' is only compatible with JVM runtime version $tooHighJava or newer.""")
        failure.assertHasErrorOutput("Caused by: " + VariantSelectionByAttributesException.class.getName())
        failure.assertHasResolution("Change the dependency on 'project :producer' to an earlier version that supports JVM runtime version $currentJava.")
    }

    def 'JVM version too low even if other non-Library category variants available uses standard error message for non-plugin'() {
        given:
        def producer = file('producer')
        def consumer = file('consumer')

        file('settings.gradle') << """
            include 'producer', 'consumer'
        """

        producer.file('build.gradle') << """
            plugins {
                id('java-library')
            }

            configurations {
                consumable("nonLibrary") {
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.VERIFICATION))
                    }
                }
            }

            java {
                sourceCompatibility = $tooHighJava
                targetCompatibility = $tooHighJava
            }
        """

        consumer.file('build.gradle') << """
            plugins {
                id('java-library')
            }

            dependencies {
                implementation project(':producer')
            }
        """

        when:
        fails ':consumer:build', "--stacktrace"

        then:
        failure.assertHasErrorOutput("""> Could not resolve all dependencies for configuration ':consumer:compileClasspath'.
   > Could not resolve project :producer.
     Required by:
         project :consumer
      > Dependency resolution is looking for a library compatible with JVM runtime version $currentJava, but 'project :producer' is only compatible with JVM runtime version $tooHighJava or newer.""")
        failure.assertHasErrorOutput("Caused by: " + VariantSelectionByAttributesException.class.getName())
        failure.assertHasResolution("Change the dependency on 'project :producer' to an earlier version that supports JVM runtime version $currentJava.")
    }
}
