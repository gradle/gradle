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

class EclipseWtpWebAndJavaProjectIntegrationTest extends AbstractEclipseIntegrationSpec {
    @ToBeFixedForConfigurationCache
    def "generates configuration files for web project and java project it depends on"() {
        settingsFile << "include 'web', 'java'"

        file('java/src/main/java').mkdirs()
        file('web/src/main/java').mkdirs()
        file('web/src/main/webapp').mkdirs()

        buildFile <<
        """subprojects {
               apply plugin: 'eclipse-wtp'

               ${AbstractIntegrationSpec.mavenCentralRepository()}
           }
           project(':web') {
               apply plugin: 'war'

               java.sourceCompatibility = 1.6

               dependencies {
                   providedCompile 'javax.servlet:javax.servlet-api:3.1.0'
                   implementation 'org.apache.commons:commons-lang3:3.0'
                   implementation project(':java')
                   testImplementation "junit:junit:4.13"
               }
           }
            project(':java') {
                apply plugin: 'java'

                java.sourceCompatibility = 1.6

                dependencies {
                    implementation 'com.google.guava:guava:18.0'
                    implementation 'javax.servlet:javax.servlet-api:3.1.0'
                    testImplementation "junit:junit:4.13"
                }
            }
            """

        when:
        run "eclipse"

        then:
        // Builders and natures
        def javaProject = project('java')
        javaProject.assertHasJavaFacetNatures()
        javaProject.assertHasJavaFacetBuilders()

        def webProject = project('web')
        webProject.assertHasJavaFacetNatures()
        webProject.assertHasJavaFacetBuilders()

        // Classpath
        def javaClasspath = classpath('java')
        javaClasspath.assertHasLibs('guava-18.0.jar', 'javax.servlet-api-3.1.0.jar', 'junit-4.13.jar', 'hamcrest-core-1.3.jar')
        javaClasspath.lib('guava-18.0.jar').assertIsExcludedFromDeployment()
        javaClasspath.lib('javax.servlet-api-3.1.0.jar').assertIsExcludedFromDeployment()
        javaClasspath.lib('junit-4.13.jar').assertIsExcludedFromDeployment()
        javaClasspath.lib('hamcrest-core-1.3.jar').assertIsExcludedFromDeployment()

        def webClasspath = classpath('web')
        webClasspath.assertHasLibs('commons-lang3-3.0.jar', 'javax.servlet-api-3.1.0.jar', "guava-18.0.jar", 'junit-4.13.jar', 'hamcrest-core-1.3.jar')
        webClasspath.lib('commons-lang3-3.0.jar').assertIsDeployedTo("/WEB-INF/lib")
        webClasspath.lib('javax.servlet-api-3.1.0.jar').assertIsExcludedFromDeployment()
        webClasspath.lib('junit-4.13.jar').assertIsExcludedFromDeployment()
        webClasspath.lib('hamcrest-core-1.3.jar').assertIsExcludedFromDeployment()

        // Facets
        def javaFacets = wtpFacets('java')
        javaFacets.assertHasFixedFacets("jst.java")
        javaFacets.assertHasInstalledFacets("jst.utility", "jst.java")

        def webFacets = wtpFacets('web')
        webFacets.assertHasFixedFacets("jst.java", "jst.web")
        webFacets.assertHasInstalledFacets("jst.web", "jst.java")

        // Component
        def javaComponent = wtpComponent('java')
        javaComponent.deployName == 'java'
        javaComponent.resources.size() == 1
        javaComponent.sourceDirectory('src/main/java').assertDeployedAt('/')
        javaComponent.modules.isEmpty()

        def webComponent = wtpComponent('web')
        webComponent.deployName == 'web'
        webComponent.resources.size() == 2
        webComponent.sourceDirectory('src/main/java').assertDeployedAt('/WEB-INF/classes')
        webComponent.sourceDirectory('src/main/webapp').assertDeployedAt('/')
        webComponent.modules.size() == 1
        webComponent.project('java').assertDeployedAt('/WEB-INF/lib')
    }
}
