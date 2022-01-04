/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.diagnostics

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.InspectsOutgoingVariants

class RequestedVariantsReportTask extends AbstractIntegrationSpec implements InspectsOutgoingVariants {
    def setup() {
        settingsFile << """
            rootProject.name = "myLib"
        """
    }


    //@ToBeFixedForConfigurationCache(because = ":requestedVariants")
    def "reports requested variants of a Java Library with module dependencies"() {
        buildFile << """
            plugins { id 'java-library' }

            ${mavenCentralRepository()}

            dependencies {
                api 'org.apache.commons:commons-lang3:3.5'
                implementation 'org.apache.commons:commons-compress:1.19'
            }
        """

        when:
        run ':requestedVariants'

        then:
        outputContains """> Task :requestedVariants
--------------------------------------------------
Configuration apiElements
--------------------------------------------------
Description = API elements for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-api
"""

        and:
        hasLegacyVariantsLegend()
        hasIncubatingVariantsLegend()
    }
}
