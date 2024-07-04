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

package org.gradle.api.invocation

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GradleLifecycleAllprojectsIntegrationTest extends AbstractIntegrationSpec {

    def 'lifecycle.allprojects is executed before lifecycle.beforeProject'() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            gradle.lifecycle.beforeProject {
                println "lifecycle.beforeProject for \${name}"
            }
            gradle.lifecycle.allprojects {
                println "lifecycle.allprojects for \${name}"
            }
            include(":a")
        """
        file("a/build.gradle") << ""

        when:
        run "help", "-q"

        then:
        outputContains """
lifecycle.allprojects for root
lifecycle.beforeProject for root
lifecycle.allprojects for a
lifecycle.beforeProject for a
"""
    }
}
