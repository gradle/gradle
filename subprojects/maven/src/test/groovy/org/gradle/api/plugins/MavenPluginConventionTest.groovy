/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.plugins

import org.gradle.api.internal.artifacts.publish.maven.DefaultMavenPom
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultConf2ScopeMappingContainer
import org.gradle.api.internal.project.DefaultProject
import org.gradle.util.HelperUtil
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
class MavenPluginConventionTest extends Specification {
    DefaultProject project = HelperUtil.createRootProject()
    MavenPluginConvention mavenPluginConvention = new MavenPluginConvention(project)

    def pomShouldCreateMavenPom() {
        mavenPluginConvention.conf2ScopeMappings = new DefaultConf2ScopeMappingContainer();
        project.group = 'someGroup'
        project.version = '1.0'
        DefaultMavenPom mavenPom = mavenPluginConvention.pom()

        expect:
        !mavenPluginConvention.conf2ScopeMappings.is(mavenPom.scopeMappings)
        mavenPluginConvention.conf2ScopeMappings == mavenPom.scopeMappings
        mavenPom.mavenProject != null
        mavenPom.pomDependenciesConverter != null
        mavenPom.configurations.is(project.getConfigurations())
        mavenPom.fileResolver == project.fileResolver
        mavenPom.groupId == project.group
        mavenPom.artifactId == project.name
        mavenPom.version == project.version
    }

    def pomShouldCreateAndConfigureMavenPom() {
        mavenPluginConvention.conf2ScopeMappings = new DefaultConf2ScopeMappingContainer();
        DefaultMavenPom mavenPom = mavenPluginConvention.pom {
            project {
                inceptionYear '1999'
            }
        }

        expect:
        mavenPom.mavenProject.inceptionYear == '1999'

    }
}
