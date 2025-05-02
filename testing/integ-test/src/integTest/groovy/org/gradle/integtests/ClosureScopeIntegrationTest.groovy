/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache


class ClosureScopeIntegrationTest extends AbstractIntegrationSpec implements StableConfigurationCacheDeprecations {

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def "check scope when closure in ext"() {
        given:
        file('closure_in_ext.gradle') << """
allprojects {
    ext.someClosure = {
        project.name
    }

    task someTask {
        doLast {
            println someClosure()
            assert someClosure() == project.name
        }
    }
}
"""
        buildFile << """
apply from:'closure_in_ext.gradle'
"""
        createDirs("sampleSub")
        settingsFile << """
rootProject.name = "rootProject"
include 'sampleSub'
"""
        when:
        expectTaskGetProjectDeprecations()
        succeeds(":sampleSub:someTask")

        then:
        noExceptionThrown()
    }
}
