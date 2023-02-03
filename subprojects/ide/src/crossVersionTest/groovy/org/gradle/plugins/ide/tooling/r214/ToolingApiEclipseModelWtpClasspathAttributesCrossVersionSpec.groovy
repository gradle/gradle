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

package org.gradle.plugins.ide.tooling.r214

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.model.eclipse.EclipseExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.EclipseProjectDependency

class ToolingApiEclipseModelWtpClasspathAttributesCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {

    String localMaven

    def setup() {
        MavenFileRepository mavenRepo = new MavenFileRepository(file("maven-repo"))
        MavenFileModule exampleApi = mavenRepo.module("org.example", "example-api", "1.0")
        MavenFileModule exampleLib = mavenRepo.module("org.example", "example-lib", "1.0")
        exampleLib.dependsOn(exampleApi)
        exampleApi.publish()
        exampleLib.publish()
        localMaven = "maven { url '${mavenRepo.uri}' }"
    }

    def "Dependencies of a non-wtp project have no wtp deployment attributes"() {
        given:
        settingsFile << "include 'sub'"
        buildFile <<
        """apply plugin: 'java'
           repositories { $localMaven }
           dependencies {
               ${implementationConfiguration} 'org.example:example-api:1.0'
               ${implementationConfiguration} project(':sub')
           }
           project(':sub') { apply plugin : 'java' }
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> externalDependencies = eclipseProject.getClasspath()
        Collection<EclipseProjectDependency> projectDependencies = eclipseProject.getProjectDependencies()

        then:
        externalDependencies.size() == 1
        entryHasNoDeploymentInfo(externalDependencies[0])
        projectDependencies.size() == 1
        entryHasNoDeploymentInfo(projectDependencies[0])
    }

    def "Web project dependencies have wtp deployment attributes"() {
        given:
        String pluginDeclaration = appliedPlugins.collect { "apply plugin: '$it'" }.join('\n')
        buildFile <<
         """apply plugin: 'java'
            $pluginDeclaration
            repositories { $localMaven }
            dependencies { ${implementationConfiguration} 'org.example:example-api:1.0' }
         """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 1
        entryHasDeploymentInfo(classpath[0])

        where:
        appliedPlugins         | _
        ['war']                | _
        ['war', 'eclipse-wtp'] | _
        ['ear']                | _
        ['ear', 'eclipse-wtp'] | _
    }

    def "Wtp utility projects do not deploy any dependencies"() {
        given:
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse-wtp'
           repositories { $localMaven }
           dependencies { ${implementationConfiguration} 'org.example:example-lib:1.0' }
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 2
        entryNotDeployed(classpath[0])
        entryNotDeployed(classpath[1])
    }

    def "Root wtp dependencies and their transitives are deployed to '/'"() {
        given:
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'war'
           apply plugin: 'eclipse-wtp'
           repositories { $localMaven }
           dependencies { ${implementationConfiguration} 'org.example:example-lib:1.0' }
           eclipse.wtp.component.rootConfigurations += [ configurations.compileClasspath ]
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 2
        entryIsDeployed(classpath[0], '/')
        entryIsDeployed(classpath[1], '/')
    }

    def "Root wtp dependencies present in minusConfigurations are excluded from deployment"() {
        given:
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'war'
           apply plugin: 'eclipse-wtp'
           repositories { $localMaven }
           dependencies {
               providedRuntime 'org.example:example-api:1.0'
               ${implementationConfiguration} 'org.example:example-lib:1.0'
           }
           eclipse.wtp.component.rootConfigurations += [ configurations.compileClasspath ]
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        entryNotDeployed(classpath.find { it.file.absolutePath.contains 'example-api' })
        entryIsDeployed(classpath.find { it.file.absolutePath.contains 'example-lib' }, '/')
    }

    def "Library wtp dependencies and their transitives are deployed to '/WEB-INF/lib'"() {
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'war'
           repositories { $localMaven }
           dependencies { ${implementationConfiguration} 'org.example:example-lib:1.0' }
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 2
        entryIsDeployed(classpath[0], '/WEB-INF/lib')
        entryIsDeployed(classpath[1], '/WEB-INF/lib')
    }

    def "Lib wtp dependencies present in minusConfigurations are excluded from deployment"() {
        given:
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'war'
           apply plugin: 'eclipse-wtp'
           repositories { $localMaven }
           dependencies {
               providedRuntime 'org.example:example-api:1.0'
               ${implementationConfiguration} 'org.example:example-lib:1.0'
           }
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        entryNotDeployed(classpath.find { it.file.absolutePath.contains 'example-api' })
        entryIsDeployed(classpath.find { it.file.absolutePath.contains 'example-lib' }, '/WEB-INF/lib')
    }

    def "Deployment folder follows ear app dir name configuration"() {
        buildFile <<
        """apply plugin: 'ear'
           apply plugin: 'java'
           apply plugin: 'eclipse'
           repositories { $localMaven }
           dependencies { earlib 'org.example:example-api:1.0' }
           eclipse.classpath.plusConfigurations << configurations.earlib
           ear { libDirName = '/custom/lib/dir' }
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 1
        entryIsDeployed(classpath[0], '/custom/lib/dir')

    }


    def "All non-wtp dependencies are marked as not deployed"() {
        given:
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'war'
           repositories { $localMaven }
           dependencies { compileOnly 'org.example:example-lib:1.0' }
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 2
        entryNotDeployed(classpath[0])
        entryNotDeployed(classpath[1])
    }

    def "Project dependencies are marked as not deployed"() {
        given:
        settingsFile << 'include "sub"'
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'war'
           repositories { $localMaven }
           dependencies {
               ${implementationConfiguration} 'org.example:example-api:1.0'
               ${implementationConfiguration} project(':sub')
           }
           project(':sub') { apply plugin : 'java' }
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        def projectDependencies = eclipseProject.getProjectDependencies()

        then:
        projectDependencies.size() == 1
        entryNotDeployed(projectDependencies[0])
    }


    private def entryHasDeploymentInfo(entry) {
        return entry.classpathAttributes.find { it.name == 'org.eclipse.jst.component.nondependency' } ||
            entry.classpathAttributes.find { it.name == 'org.eclipse.jst.component.dependency' }
    }


    private def entryHasNoDeploymentInfo(entry) {
        return !entry.classpathAttributes.find { it.name == 'org.eclipse.jst.component.nondependency' } &&
            !entry.classpathAttributes.find { it.name == 'org.eclipse.jst.component.dependency' }
    }


    private def entryNotDeployed(entry) {
        return entry.classpathAttributes.find { it.name == 'org.eclipse.jst.component.nondependency' && it.value == '' } &&
            !entry.classpathAttributes.find { it.name == 'org.eclipse.jst.component.dependency' }
    }

    private def entryIsDeployed(entry, path) {
        return !entry.classpathAttributes.find { it.name == 'org.eclipse.jst.component.nondependency' } &&
            entry.classpathAttributes.find { it.name == 'org.eclipse.jst.component.dependency'  && it.value == path }
    }

}
