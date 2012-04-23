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


package org.gradle.integtests.tooling.r11

import org.gradle.integtests.fixtures.MavenRepository
import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.ExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.idea.IdeaProject
import spock.lang.Ignore

@MinToolingApiVersion('1.1-rc-1')
@MinTargetGradleVersion('1.1-rc-1')
class GradleModulesCrossVersionSpec extends ToolingApiSpecification {

    def "idea external dependencies provide gradle module information"() {
        def projectDir = dist.testDir
        def fakeRepo = projectDir.file("repo")

        def dependency = new MavenRepository(fakeRepo).module("foo.bar", "fancyLib", 2.0)
        dependency.artifact(classifier: 'sources')
        dependency.artifact(classifier: 'javadoc')
        dependency.publish()

        projectDir.file('build.gradle').text = """
apply plugin: 'java'

repositories {
    maven { url "${fakeRepo.toURI()}" }
}

dependencies {
    compile 'foo.bar:fancyLib:2.0'
}
"""
        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        def module = project.modules[0]

        then:
        module.dependencies.size() == 1
        ExternalDependency dep = module.dependencies[0]

        dep.externalGradleModule.group == "foo.bar"
        dep.externalGradleModule.name == "fancyLib"
        dep.externalGradleModule.version == "2.0"
    }

    @Ignore
    def "eclipse external dependencies provide gradle module information"() {
        def projectDir = dist.testDir
        def fakeRepo = projectDir.file("repo")

        def dependency = new MavenRepository(fakeRepo).module("foo.bar", "fancyLib", 2.0)
        dependency.artifact(classifier: 'sources')
        dependency.artifact(classifier: 'javadoc')
        dependency.publish()

        projectDir.file('build.gradle').text = """
apply plugin: 'java'

repositories {
    maven { url "${fakeRepo.toURI()}" }
}

dependencies {
    compile 'foo.bar:fancyLib:2.0'
}
"""
        when:
        EclipseProject project = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        project.classpath.size() == 1
        ExternalDependency dep = project.classpath[0]

        dep.externalGradleModule.group == "foo.bar"
        dep.externalGradleModule.name == "fancyLib"
        dep.externalGradleModule.version == "2.0"
    }
}
