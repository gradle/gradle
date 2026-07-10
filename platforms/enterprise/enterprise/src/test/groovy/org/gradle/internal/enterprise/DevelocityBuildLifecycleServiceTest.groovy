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

package org.gradle.internal.enterprise

import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.internal.configuration.DefaultBuildFeature
import org.gradle.api.invocation.Gradle
import org.gradle.api.invocation.GradleLifecycle
import org.gradle.internal.enterprise.impl.DefaultDevelocityBuildLifecycleService
import org.gradle.util.TestUtil
import spock.lang.Specification

class DevelocityBuildLifecycleServiceTest extends Specification {

    def 'uses #correctApi API for `beforeProject` logic execution if IsolatedProjects enabled=#ipEnabled'() {
        given:
        def gradle = Mock(Gradle) {
            _ * getLifecycle() >> Mock(GradleLifecycle)
        }
        def buildFeatures = Mock(BuildFeatures) {
            _ * getIsolatedProjects() >> new DefaultBuildFeature(
                TestUtil.providerFactory().provider { ipEnabled },
                TestUtil.providerFactory().provider { ipEnabled }
            )
        }
        def service = new DefaultDevelocityBuildLifecycleService(gradle, buildFeatures)

        when:
        service.beforeProject {/*do something*/ }

        then:
        if (ipEnabled) {
            0 * gradle.allprojects(_)
            1 * gradle.lifecycle.beforeProject(_)
        } else {
            1 * gradle.allprojects(_)
            0 * gradle.lifecycle.beforeProject(_)
        }

        where:
        ipEnabled | correctApi
        true      | "GradleLifecycle#beforeProject"
        false     | "Gradle#allprojects"
    }
}
