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

package org.gradle.integtests.tooling.r214

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.model.eclipse.EclipseExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.EclipseProjectDependency

@ToolingApiVersion('>=2.14')
@TargetGradleVersion('>=2.14')
class ToolingApiEclipseModelWtpClasspathAttributesCrossVersionSpec extends ToolingApiSpecification {

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
               compile 'org.example:example-api:1.0'
               compile project(':sub')
           }
           project(':sub') { apply plugin : 'java' }
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> externalDependencies = eclipseProject.getClasspath()
        Collection<EclipseProjectDependency> projectDependencies = eclipseProject.getProjectDependencies()

        then:
        externalDependencies.size() == 1
        externalDependencies[0].classpathAttributes.isEmpty()
        projectDependencies.size() == 1
        projectDependencies[0].classpathAttributes.isEmpty()
    }

    def "Web project dependencies have wtp deployment attributes"() {
        given:
        String pluginDeclaration = appliedPlugins.collect { "apply plugin: '$it'" }.join('\n')
        buildFile <<
         """apply plugin: 'java'
            $pluginDeclaration
            repositories { $localMaven }
            dependencies { compile 'org.example:example-api:1.0' }
         """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 1
        classpath[0].classpathAttributes.size() == 1
        classpath[0].classpathAttributes[0].name.startsWith 'org.eclipse.jst.component.'

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
           dependencies { compile 'org.example:example-lib:1.0' }
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 2
        classpath[0].classpathAttributes.size() == 1
        classpath[0].classpathAttributes[0].name == 'org.eclipse.jst.component.nondependency'
        classpath[0].classpathAttributes[0].value == ''
        classpath[1].classpathAttributes.size() == 1
        classpath[1].classpathAttributes[0].name == 'org.eclipse.jst.component.nondependency'
        classpath[1].classpathAttributes[0].value == ''
    }

    def "Root wtp dependencies and their transitives are deployed to '/'"() {
        given:
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'war'
           apply plugin: 'eclipse-wtp'
           repositories { $localMaven }
           dependencies { compile 'org.example:example-lib:1.0' }
           eclipse.wtp.component.rootConfigurations += [ configurations.compile ]
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 2
        classpath[0].classpathAttributes.size() == 1
        classpath[0].classpathAttributes[0].name == 'org.eclipse.jst.component.dependency'
        classpath[0].classpathAttributes[0].value == '/'
        classpath[1].classpathAttributes.size() == 1
        classpath[1].classpathAttributes[0].name == 'org.eclipse.jst.component.dependency'
        classpath[1].classpathAttributes[0].value == '/'
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
               compile 'org.example:example-lib:1.0'
           }
           eclipse.wtp.component.rootConfigurations += [ configurations.compile ]
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 2
        classpath[0].file.absolutePath.contains 'example-lib'
        classpath[0].classpathAttributes.size() == 1
        classpath[0].classpathAttributes[0].name == 'org.eclipse.jst.component.dependency'
        classpath[0].classpathAttributes[0].value == '/'
        classpath[1].file.absolutePath.contains 'example-api'
        classpath[1].classpathAttributes.size() == 1
        classpath[1].classpathAttributes[0].name == 'org.eclipse.jst.component.nondependency'
        classpath[1].classpathAttributes[0].value == ''
    }

    def "Library wtp dependencies and their transitives are deployed to '/WEB-INF/lib'"() {
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'war'
           repositories { $localMaven }
           dependencies { compile 'org.example:example-lib:1.0' }
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 2
        classpath[0].classpathAttributes.size() == 1
        classpath[0].classpathAttributes[0].name == 'org.eclipse.jst.component.dependency'
        classpath[0].classpathAttributes[0].value == '/WEB-INF/lib'
        classpath[1].classpathAttributes.size() == 1
        classpath[1].classpathAttributes[0].name == 'org.eclipse.jst.component.dependency'
        classpath[1].classpathAttributes[0].value == '/WEB-INF/lib'
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
               compile 'org.example:example-lib:1.0'
           }
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 2
        classpath[0].file.absolutePath.contains 'example-lib'
        classpath[0].classpathAttributes.size() == 1
        classpath[0].classpathAttributes[0].name == 'org.eclipse.jst.component.dependency'
        classpath[0].classpathAttributes[0].value == '/WEB-INF/lib'
        classpath[1].file.absolutePath.contains 'example-api'
        classpath[1].classpathAttributes.size() == 1
        classpath[1].classpathAttributes[0].name == 'org.eclipse.jst.component.nondependency'
        classpath[1].classpathAttributes[0].value == ''
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
        classpath[0].classpathAttributes.size() == 1
        classpath[0].classpathAttributes[0].name == 'org.eclipse.jst.component.dependency'
        classpath[0].classpathAttributes[0].value == '/custom/lib/dir'
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
        classpath[0].classpathAttributes.size() == 1
        classpath[0].classpathAttributes[0].name == 'org.eclipse.jst.component.nondependency'
        classpath[1].classpathAttributes.size() == 1
        classpath[1].classpathAttributes[0].name == 'org.eclipse.jst.component.nondependency'
    }

    def "Project dependencies are marked as not deployed"() {
        given:
        settingsFile << 'include "sub"'
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'war'
           repositories { $localMaven }
           dependencies {
               compile 'org.example:example-api:1.0'
               compile project(':sub')
           }
           project(':sub') { apply plugin : 'java' }
        """

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        def projectDependencies = eclipseProject.getProjectDependencies()

        then:
        projectDependencies.size() == 1
        projectDependencies[0].classpathAttributes.size() == 1
        projectDependencies[0].classpathAttributes[0].name.startsWith 'org.eclipse.jst.component.nondependency'

    }

}
