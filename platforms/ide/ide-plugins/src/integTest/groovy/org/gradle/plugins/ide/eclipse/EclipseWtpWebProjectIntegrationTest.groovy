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

class EclipseWtpWebProjectIntegrationTest extends AbstractEclipseIntegrationSpec {
    @ToBeFixedForConfigurationCache
    def "generates configuration files for a web project"() {
        file('src/main/java').mkdirs()
        file('src/main/resources').mkdirs()
        file('src/main/webapp').mkdirs()

        settingsFile << "rootProject.name = 'web'"

        buildFile <<
        """apply plugin: 'war'
           apply plugin: 'eclipse-wtp'

           java.sourceCompatibility = 1.6

           ${AbstractIntegrationSpec.mavenCentralRepository()}

           dependencies {
               implementation 'com.google.guava:guava:18.0'
               compileOnly 'jstl:jstl:1.2'
               providedCompile 'javax.servlet:javax.servlet-api:3.1.0'
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
        classpath.assertHasLibs('jstl-1.2.jar', 'guava-18.0.jar', 'javax.servlet-api-3.1.0.jar', 'junit-4.13.jar', 'hamcrest-core-1.3.jar')
        classpath.lib('guava-18.0.jar').assertIsDeployedTo("/WEB-INF/lib")
        classpath.lib('jstl-1.2.jar').assertIsExcludedFromDeployment()
        classpath.lib('junit-4.13.jar').assertIsExcludedFromDeployment()
        classpath.lib('hamcrest-core-1.3.jar').assertIsExcludedFromDeployment()

        // Facets
        def facets = wtpFacets
        facets.assertHasFixedFacets("jst.java", "jst.web")
        facets.assertHasInstalledFacets("jst.web", "jst.java")
        facets.assertFacetVersion("jst.web", "2.4")
        facets.assertFacetVersion("jst.java", "6.0")

        // Component
        def component = wtpComponent
        component.deployName == 'web'
        component.resources.size() == 3
        component.sourceDirectory('src/main/java').assertDeployedAt('/WEB-INF/classes')
        component.sourceDirectory('src/main/resources').assertDeployedAt('/WEB-INF/classes')
        component.sourceDirectory('src/main/webapp').assertDeployedAt('/')
        component.modules.isEmpty();
    }
}
