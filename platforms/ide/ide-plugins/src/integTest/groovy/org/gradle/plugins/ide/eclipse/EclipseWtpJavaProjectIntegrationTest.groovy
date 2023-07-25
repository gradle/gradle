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
package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class EclipseWtpJavaProjectIntegrationTest extends AbstractEclipseIntegrationSpec {

    @ToBeFixedForConfigurationCache
    def "generates configuration files for a Java project"() {
        file('src/main/java').mkdirs()
        file('src/main/resources').mkdirs()

        settingsFile << "rootProject.name = 'java'"

        buildFile <<
        """apply plugin: 'eclipse-wtp'
           apply plugin: 'java'

           ${AbstractIntegrationSpec.mavenCentralRepository()}

           java.sourceCompatibility = 1.6

           dependencies {
               implementation 'com.google.guava:guava:18.0'
               testImplementation "junit:junit:4.13"
           }
        """

        when:
        run "eclipse"

        then:
        // Builders and natures
        def project = project
        project.assertHasJavaFacetNatures()
        project.assertHasJavaFacetBuilders()

        // Classpath
        def classpath = classpath
        classpath.assertHasLibs('guava-18.0.jar', 'junit-4.13.jar', 'hamcrest-core-1.3.jar')
        classpath.lib('guava-18.0.jar').assertIsExcludedFromDeployment()
        classpath.lib('junit-4.13.jar').assertIsExcludedFromDeployment()
        classpath.lib('hamcrest-core-1.3.jar').assertIsExcludedFromDeployment()

        // Facets
        def facets = wtpFacets
        facets.assertHasFixedFacets("jst.java")
        facets.assertHasInstalledFacets("jst.utility", "jst.java")
        facets.assertFacetVersion("jst.utility", "1.0")
        facets.assertFacetVersion("jst.java", "6.0")

        // Component
        def component = wtpComponent
        component.deployName == 'java'
        component.resources.size() == 2
        component.sourceDirectory('src/main/java').assertDeployedAt('/')
        component.sourceDirectory('src/main/resources').assertDeployedAt('/')
        component.modules.isEmpty()
    }
}
