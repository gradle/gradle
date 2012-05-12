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
package org.gradle.integtests.tooling.r11rc1

import org.gradle.integtests.fixtures.MavenRepository
import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.ExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.idea.IdeaProject

@MinToolingApiVersion('current')
@MinTargetGradleVersion('current')
class ExternalGradleModulesCrossVersionSpec extends ToolingApiSpecification {

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
        EclipseProject project = withConnection { connection -> connection.getModel(EclipseProject.class) }
        def libs = project.classpath

        then:
        containModuleInfo(libs)
    }

    private void prepareBuild() {
        def fakeRepo = dist.file("repo")
        new MavenRepository(fakeRepo).module("foo.bar", "coolLib", 2.0).publish()

        dist.file("yetAnotherJar.jar").createFile()

        dist.file('build.gradle').text = """
apply plugin: 'java'

repositories {
    maven { url "${fakeRepo.toURI()}" }
}

dependencies {
    compile 'foo.bar:coolLib:2.0'
    compile 'unresolved.org:funLib:1.0'
    compile files('yetAnotherJar.jar')
}
"""
    }

    private void containModuleInfo(libs) {
        assert libs.size() == 3

        ExternalDependency coolLib = libs.find { it.externalGradleModule?.name == 'coolLib' }
        assert coolLib.externalGradleModule.group == 'foo.bar'
        assert coolLib.externalGradleModule.name == 'coolLib'
        assert coolLib.externalGradleModule.version == '2.0'

        ExternalDependency funLib = libs.find { it.externalGradleModule?.name == 'funLib' }
        assert funLib.externalGradleModule.group == 'unresolved.org'
        assert funLib.externalGradleModule.name == 'funLib'
        assert funLib.externalGradleModule.version == '1.0'

        ExternalDependency yetAnotherJar = libs.find { it.externalGradleModule == null }
        assert yetAnotherJar.file.name == 'yetAnotherJar.jar'
    }
}