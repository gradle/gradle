/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ide.tooling.m5

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.lang.Issue

class EclipseModelWithFlatRepoCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {

    @Issue("GRADLE-1621")
    def "can get Eclipse model for project with flatDir repo and external dependency without source Jar"() {
        def repoDir = projectDir.createDir("repo")
        repoDir.createFile("lib-1.0.jar")

        projectDir.file("build.gradle") << """
apply plugin: "java"

repositories {
	flatDir dirs: file("${repoDir.toURI()}")
}

dependencies {
	${implementationConfiguration} "some:lib:1.0"
}
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.classpath[0].file != null
        project.classpath[0].source == null
        project.classpath[0].javadoc == null
    }
}
