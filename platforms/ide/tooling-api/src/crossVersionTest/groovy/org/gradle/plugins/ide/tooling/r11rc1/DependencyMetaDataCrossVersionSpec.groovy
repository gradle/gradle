/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.plugins.ide.tooling.r11rc1

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.model.ExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.idea.IdeaProject

class DependencyMetaDataCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {

    def "idea libraries contain gradle module information"() {
        given:
        prepareBuild()

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        def module = project.modules[0]
        def libs = module.dependencies

        then:
        containModuleInfo(libs)
    }

    def "eclipse libraries contain gradle module information"() {
        given:
        prepareBuild()

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        def libs = project.classpath

        then:
        containModuleInfo(libs)
    }

    private void prepareBuild() {
        def fakeRepo = file("repo")
        new MavenFileRepository(fakeRepo).module("foo.bar", "coolLib", "2.0").publish()

        file("yetAnotherJar.jar").createFile()

        file('build.gradle').text = """
apply plugin: 'java'

repositories {
    maven { url = "${fakeRepo.toURI()}" }
}

dependencies {
    ${implementationConfiguration} 'foo.bar:coolLib:2.0'
    ${implementationConfiguration} 'unresolved.org:funLib:1.0'
    ${implementationConfiguration} files('yetAnotherJar.jar')
}
"""
    }

    private void containModuleInfo(libs) {
        ExternalDependency coolLib = libs.find { it.file.name == 'coolLib-2.0.jar' }
        assert coolLib.gradleModuleVersion
        assert coolLib.gradleModuleVersion.group == 'foo.bar'
        assert coolLib.gradleModuleVersion.name == 'coolLib'
        assert coolLib.gradleModuleVersion.version == '2.0'

        ExternalDependency funLib = libs.find { it.file.name.contains('funLib') }
        assert funLib.gradleModuleVersion == null

        ExternalDependency yetAnotherJar = libs.find { it.file.name == 'yetAnotherJar.jar' }
        assert yetAnotherJar.gradleModuleVersion == null
    }
}
