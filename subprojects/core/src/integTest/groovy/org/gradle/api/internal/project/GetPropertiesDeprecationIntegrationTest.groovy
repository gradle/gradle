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

package org.gradle.api.internal.project

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GetPropertiesDeprecationIntegrationTest extends AbstractIntegrationSpec {

    private static final String PROJECT_DEPRECATION = "The Project.getProperties method has been deprecated. " +
        "This will fail with an error in Gradle 10. " +
        "Consult the upgrading guide for further information: " +
        "https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_project_get_properties"

    private static final String SCRIPT_DEPRECATION = "Dynamically calling getProperties() on a script has been deprecated. " +
        "This will fail with an error in Gradle 10. " +
        "Consult the upgrading guide for further information: " +
        "https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_script_get_properties"

    def "accessing properties on a #scriptType is deprecated"() {
        file(scriptFile) << "properties"

        expect:
        executer.expectDocumentedDeprecationWarning(expectedDeprecation)
        succeeds("help")

        where:
        scriptType               | scriptFile            | expectedDeprecation
        "Kotlin build script"    | "build.gradle.kts"    | PROJECT_DEPRECATION
        "Groovy build script"    | "build.gradle"        | SCRIPT_DEPRECATION
        "Groovy settings script" | "settings.gradle"     | SCRIPT_DEPRECATION
    }

    def "accessing properties on an init script is deprecated"() {
        def initScript = file("init.gradle") << "properties"

        when:
        executer.expectDocumentedDeprecationWarning(SCRIPT_DEPRECATION)
        fails("help", "-I", initScript.absolutePath)

        then:
        failure.assertHasCause("The default project is not yet available for build")
    }
}
