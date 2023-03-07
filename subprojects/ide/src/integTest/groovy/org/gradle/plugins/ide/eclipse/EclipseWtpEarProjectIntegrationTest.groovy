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
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.MavenFileRepository

class EclipseWtpEarProjectIntegrationTest extends AbstractEclipseIntegrationSpec {

    String localMaven

    def setup() {
        MavenFileRepository mavenRepo = new MavenFileRepository(file('maven-repo'))
        MavenFileModule lib1Api = mavenRepo.module('org.example', 'lib1-api', '1.0').publish()
        mavenRepo.module('org.example', 'lib1-impl', '1.0').dependsOn(lib1Api).publish()
        MavenFileModule lib2Api = mavenRepo.module('org.example', 'lib2-api', '2.0').publish()
        mavenRepo.module('org.example', 'lib2-impl', '2.0').dependsOn(lib2Api).publish()
        localMaven = "maven { url '${mavenRepo.uri}' }"
    }

    @ToBeFixedForConfigurationCache
    def "generates configuration files for an non-java ear project"() {
        settingsFile << "rootProject.name = 'ear'"

        buildFile <<
        """apply plugin: 'eclipse-wtp'
           apply plugin: 'ear'

           repositories { $localMaven }

           dependencies {
               earlib 'org.example:lib1-impl:1.0'
               deploy 'org.example:lib2-impl:2.0'
           }
        """

        when:
        run 'eclipse'

        then:
        // Builders and natures
        def project = project
        project.assertHasJavaFacetNatures()
        project.assertHasJavaFacetBuilders()

        // Classpath
        testDirectory.file('.classpath').exists()

        // Facets
        def facets = wtpFacets
        facets.assertHasFixedFacets('jst.ear')
        facets.assertHasInstalledFacets('jst.ear')
        facets.assertFacetVersion('jst.ear', '5.0')

        // Component
        def component = wtpComponent
        component.deployName == 'ear'
        component.resources.isEmpty()
        component.modules.size() == 3
        component.lib('lib1-api-1.0.jar').assertDeployedAt('/lib')
        component.lib('lib1-impl-1.0.jar').assertDeployedAt('/lib')
        component.lib('lib2-impl-2.0.jar').assertDeployedAt('/')
    }

    @ToBeFixedForConfigurationCache
    def "ear deployment location can be configured via libDirName"() {
        settingsFile << "rootProject.name = 'ear'"

        buildFile <<
        """apply plugin: 'eclipse-wtp'
           apply plugin: 'ear'

           repositories { $localMaven }

           dependencies {
               earlib 'org.example:lib1-impl:1.0'
               deploy 'org.example:lib2-impl:2.0'
           }

           ear {
               libDirName = 'APP-INF/lib'
           }
        """

        when:
        run 'eclipse'

        then:
        def component = wtpComponent
        component.lib('lib1-api-1.0.jar').assertDeployedAt('/APP-INF/lib')
        component.lib('lib1-impl-1.0.jar').assertDeployedAt('/APP-INF/lib')
        component.lib('lib2-impl-2.0.jar').assertDeployedAt('/')
    }
}
