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
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.model.eclipse.EclipseExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion('>=2.14')
@TargetGradleVersion('>=2.14')
class ToolingApiEclipseModelWtpClasspathAttributesCrossVersionSpec extends ToolingApiSpecification {

    def mavenRepo

    def setup() {
        settingsFile << ''
        mavenRepo = new MavenFileRepository(file("maven-repo"))
        mavenRepo.module("org.example", "example-lib", "1.0").publish()
    }

    def "Dependencies of a non-wtp project have no wtp deployment attributes"() {
        given:
        buildFile << """apply plugin: 'java'
repositories { maven { url '${mavenRepo.uri}' } }
dependencies { compile 'org.example:example-lib:1.0' }"""

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> externalDependencies = eclipseProject.getClasspath()

        then:
        externalDependencies.size() == 1
        externalDependencies[0].classpathAttributes.size() == 0
    }

    def "War project dependencies have wtp deployment attributes"() {
        given:
        buildFile << """
apply plugin: 'java'
apply plugin: 'war'
${eclipseWtpPluginApplied ? "apply plugin : 'eclipse-wtp'" : ""}
repositories { maven { url '${mavenRepo.uri}' } }
dependencies { compile 'org.example:example-lib:1.0' }"""

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 1
        classpath[0].classpathAttributes.size() == 1
        classpath[0].classpathAttributes[0].name.startsWith 'org.eclipse.jst.component.'

        where:
        eclipseWtpPluginApplied << [false, true]
    }

    def "Ear project dependencies have wtp deployment attributes"() {
        buildFile << """
apply plugin: 'java'
apply plugin: 'ear'
${eclipseWtpPluginApplied ? "apply plugin : 'eclipse-wtp'" : ""}
repositories { maven { url '${mavenRepo.uri}' } }
dependencies { compile 'org.example:example-lib:1.0' }"""

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 1
        classpath[0].classpathAttributes.size() == 1
        classpath[0].classpathAttributes[0].name.startsWith 'org.eclipse.jst.component.'

        where:
        eclipseWtpPluginApplied << [false, true]
    }

    def "Wtp utility projects do not deploy any dependencies"() {
        given:
        buildFile << """
apply plugin: 'java'
apply plugin: 'eclipse-wtp'
repositories { maven { url '${mavenRepo.uri}' } }
dependencies { compile 'org.example:example-lib:1.0' }
eclipse {
    wtp {
        component {
            plusConfigurations += [ configurations.compile ]
        }
    }
}"""

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 1
        classpath[0].classpathAttributes.size() == 1
        classpath[0].classpathAttributes[0].name == 'org.eclipse.jst.component.nondependency'
        classpath[0].classpathAttributes[0].value == ''
    }

    def "Root wtp dependencies are deployed to '/'"() {
        given:
        buildFile << """
apply plugin: 'java'
apply plugin: 'war'
apply plugin: 'eclipse-wtp'
repositories { maven { url '${mavenRepo.uri}' } }
dependencies { compile 'org.example:example-lib:1.0' }
eclipse {
    wtp {
        component {
            rootConfigurations += [ configurations.compile ]
        }
    }
}"""

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 1
        classpath[0].classpathAttributes.size() == 1
        classpath[0].classpathAttributes[0].name == 'org.eclipse.jst.component.dependency'
        classpath[0].classpathAttributes[0].value == '/'
    }

    def "Library wtp dependencies are deployed to '/WEB-INF/lib'"() {
        buildFile << """
apply plugin: 'java'
apply plugin: 'war'
apply plugin: 'eclipse-wtp'
repositories { maven { url '${mavenRepo.uri}' } }
dependencies { compile 'org.example:example-lib:1.0' }
eclipse {
    wtp {
        component {
            libConfigurations += [ configurations.compile ]
        }
    }
}"""

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 1
        classpath[0].classpathAttributes.size() == 1
        classpath[0].classpathAttributes[0].name == 'org.eclipse.jst.component.dependency'
        classpath[0].classpathAttributes[0].value == '/WEB-INF/lib'
    }

    def "All non-wtp dependencies are marked as not deployed"() {
        given:
        buildFile << """
apply plugin: 'java'
apply plugin: 'war'
repositories { maven { url '${mavenRepo.uri}' } }
dependencies { compileOnly 'org.example:example-lib:1.0' }"""

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        Collection<EclipseExternalDependency> classpath = eclipseProject.getClasspath()

        then:
        classpath.size() == 1
        classpath[0].classpathAttributes.size() == 1
        classpath[0].classpathAttributes[0].name == 'org.eclipse.jst.component.nondependency'
    }

    def "Project dependencies are marked as not deployed"() {
        given:
        settingsFile << 'include "sub"'
        buildFile << """
apply plugin: 'java'
apply plugin: 'war'
repositories { maven { url '${mavenRepo.uri}' } }
dependencies {
    compile 'org.example:example-lib:1.0'
    compile project(':sub')
}

project(':sub') {
    apply plugin : 'java'
}"""

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)
        def projectDependencies = eclipseProject.getProjectDependencies()

        then:
        projectDependencies.size() == 1
        projectDependencies[0].classpathAttributes.size() == 1
        projectDependencies[0].classpathAttributes[0].name.startsWith 'org.eclipse.jst.component.nondependency'

    }

}
