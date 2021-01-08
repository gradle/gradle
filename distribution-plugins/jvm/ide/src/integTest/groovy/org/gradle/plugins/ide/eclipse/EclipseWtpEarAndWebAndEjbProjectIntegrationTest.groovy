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

class EclipseWtpEarAndWebAndEjbProjectIntegrationTest extends AbstractEclipseIntegrationSpec {
    @ToBeFixedForConfigurationCache
    def "generates configuration files for an ear project and ejb and web projects it bundles"() {
        settingsFile << "include 'ear', 'web', 'java'"

        file('java/src/main/java').mkdirs()
        file('web/src/main/java').mkdirs()
        file('web/src/main/webapp').mkdirs()

        buildFile << """
subprojects {
    apply plugin: 'eclipse-wtp'

   ${mavenCentralRepository()}
}
project(':ear') {
    apply plugin: 'ear'

    dependencies {
        deploy project(':java')
        deploy project(path: ':web', configuration: 'archives')
    }
}
project(':web') {
    apply plugin: 'war'

    dependencies {
        providedCompile 'javax.servlet:javax.servlet-api:3.1.0'
        testImplementation "junit:junit:4.13"
    }
}
project(':java') {
    apply plugin: 'java'

    dependencies {
        implementation 'com.google.guava:guava:18.0'
        implementation files('foo')
        implementation 'javax.servlet:javax.servlet-api:3.1.0'
        testImplementation "junit:junit:4.13"
    }
}
"""

        when:
        run "eclipse"

        then:
        // This test covers actual behaviour, not necessarily desired behaviour

        // Builders and natures
        def javaProject = project('java')
        def webProject = project('web')
        def earProject = project('ear')

        // Classpath
        def javaClasspath = classpath('java')
        def webClasspath = classpath('web')

        // Facets
        def javaFacets = wtpFacets('java')
        def webFacets = wtpFacets('web')
        def earFacets = wtpFacets('ear')

        // Deployment
        def javaComponent = wtpComponent('java')
        javaComponent.deployName == 'java'
        javaComponent.resources.size() == 1
        javaComponent.sourceDirectory('src/main/java').assertDeployedAt('/')
        javaComponent.modules.isEmpty()

        def webComponent = wtpComponent('web')
        webComponent.deployName == 'web'
        webComponent.resources.size() == 2
        webComponent.sourceDirectory('src/main/webapp').assertDeployedAt('/')
        webComponent.sourceDirectory('src/main/java').assertDeployedAt('/WEB-INF/classes')
        webComponent.modules.isEmpty()

        def earComponent = wtpComponent('ear')
        earComponent.deployName == 'ear'
        earComponent.resources.isEmpty()
        earComponent.modules.size() == 2
        earComponent.project('java').assertDeployedAt('/')
        earComponent.project('web').assertDeployedAt('/')
    }
}
