/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class EclipseWtpEmptyProjectIntegrationTest extends AbstractEclipseIntegrationSpec {
    @ToBeFixedForConfigurationCache
    def "generates configuration files for an empty project"() {
        settingsFile << "rootProject.name = 'empty'"

        buildFile << "apply plugin: 'eclipse-wtp'"

        when:
        run "eclipse"

        then:
        // Builders and natures
        def project = project
        project.assertHasNatures()
        project.assertHasBuilders()

        // Classpath
        !testDirectory.file('.classpath').exists()

        // Facets
        def facets = wtpFacets
        facets.assertHasFixedFacets()
        facets.assertHasInstalledFacets()

        // Component
        def component = wtpComponent
        component.deployName == 'empty'
        component.resources.isEmpty()
        component.modules.isEmpty()
    }
}
