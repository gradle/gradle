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

class EclipseWtpEarProjectIntegrationTest extends AbstractEclipseIntegrationSpec {
    def "generates configuration files for an ear project"() {
        settingsFile << "rootProject.name = 'ear'"

        buildFile << """
apply plugin: 'eclipse-wtp'
apply plugin: 'ear'

repositories {
    jcenter()
}

dependencies {
    deploy 'com.google.guava:guava:18.0'
}
"""

        when:
        run "eclipse"

        then:
        // This test covers actual behaviour, not necessarily desired behaviour

        // Builders and natures
        def project = project
        project.assertHasNatures("org.eclipse.wst.common.project.facet.core.nature",
                "org.eclipse.wst.common.modulecore.ModuleCoreNature",
                "org.eclipse.jem.workbench.JavaEMFNature")
        project.assertHasBuilders("org.eclipse.wst.common.project.facet.core.builder",
                "org.eclipse.wst.validation.validationbuilder")

        // TODO - classpath

        // Facets
        def facets = wtpFacets
        facets.assertHasFixedFacets("jst.ear")
        facets.assertHasInstalledFacets("jst.ear")
        facets.assertFacetVersion("jst.ear", "5.0")

        // Deployment
        def component = wtpComponent
        component.deployName == 'ear'
        component.resources.isEmpty()
        component.modules.size() == 1
        component.lib('guava-18.0.jar').assertDeployedAt('/')
    }
}
