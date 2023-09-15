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

package org.gradle.caching.internal.services

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildCacheServicesIntegrationTest extends AbstractIntegrationSpec {

    def "build cache services class is instantiated just once with included builds"() {
        file("build-logic/settings.gradle") << """
            rootProject.name = 'build-logic'
            include('buildquality')
        """
        file("build-logic/buildquality/build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }
            repositories {
                mavenCentral()
            }
        """
        file("build-logic/buildquality/src/main/kotlin/gradlebuild.codequality.gradle.kts") << """
            plugins {
                id("base")
            }
        """
        file("settings.gradle") << """
            rootProject.name = 'project'
            includeBuild 'build-logic'
        """
        file("build.gradle") << """
            plugins {
                id('gradlebuild.codequality')
            }
        """

        expect:
        run()
    }

    def "build cache services class is instantiated just once with buildSrc"() {
        file("buildSrc/build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }
            repositories {
                mavenCentral()
            }
        """
        file("buildSrc/src/main/kotlin/gradlebuild.codequality.gradle.kts") << """
            plugins {
                id("base")
            }
        """
        file("build.gradle") << """
            plugins {
                id('gradlebuild.codequality')
            }
        """

        expect:
        run()
    }
}
