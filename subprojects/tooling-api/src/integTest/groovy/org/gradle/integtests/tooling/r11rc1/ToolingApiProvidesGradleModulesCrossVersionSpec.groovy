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
import org.gradle.tooling.model.idea.IdeaProject

@MinToolingApiVersion('1.1-rc-1')
@MinTargetGradleVersion('1.1-rc-1')
class ToolingApiProvidesGradleModulesCrossVersionSpec extends ToolingApiSpecification {

    def "idea libraries contain gradle module information"() {
        def fakeRepo = dist.file("repo")

        def dependency = new MavenRepository(fakeRepo).module("foo.bar", "coolLib", 2.0)
        dependency.publish()

        dist.file('build.gradle').text = """
apply plugin: 'java'

repositories {
    maven { url "${fakeRepo.toURI()}" }
}

dependencies {
    compile 'foo.bar:coolLib:2.0'
    //TODO SF add support for unresolved dependencies:
//    compile 'unresolved.org:funLib:1.0'
}
"""
        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        def module = project.modules[0]
        def libs = module.dependencies

        then:
        libs.size() == 1
        ExternalDependency lib = libs[0]
        lib.externalGradleModule.group == 'foo.bar'
        lib.externalGradleModule.name == 'coolLib'
        lib.externalGradleModule.version == '2.0'
    }
}