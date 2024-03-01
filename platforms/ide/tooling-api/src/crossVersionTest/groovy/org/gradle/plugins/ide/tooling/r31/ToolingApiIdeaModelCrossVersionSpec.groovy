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

package org.gradle.plugins.ide.tooling.r31

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency
import org.gradle.util.GradleVersion

class ToolingApiIdeaModelCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {
    @ToolingApiVersion(">=3.1")
    @TargetGradleVersion(">=2.6")
    def "Provides target module name for module dependencies"() {


        file('build.gradle').text = """
subprojects {
    apply plugin: 'java'
}

project(':impl') {
    apply plugin: 'idea'

    dependencies {
        ${implementationConfiguration} project(':api')
    }
}
"""
        file('settings.gradle').text = "include 'api', 'impl'"

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        def module = project.children.find { it.name == 'impl' }

        then:
        def libs = module.dependencies

        IdeaModuleDependency mod = libs.find {it instanceof IdeaModuleDependency}
        mod.targetModuleName == 'api'
    }

    @ToolingApiVersion(">3.0")
    @TargetGradleVersion(">=2.6")
    def "can query dependencies for model produced from BuildAction"() {
        def fakeRepo = new MavenFileRepository(file("repo"))
        def dependency = fakeRepo.module("foo.bar", "coolLib", "1.0")
        dependency.artifact(classifier: 'sources')
        dependency.artifact(classifier: 'javadoc')
        dependency.publish()

        file('build.gradle').text = """
subprojects {
    apply plugin: 'java'
}

project(':impl') {
    apply plugin: 'idea'

    repositories {
        maven { url "${fakeRepo.uri}" }
    }

    dependencies {
        ${implementationConfiguration} project(':api')
        ${testImplementationConfiguration} 'foo.bar:coolLib:1.0'
    }

    idea.module.downloadJavadoc = true
}
"""
        file('settings.gradle').text = "include 'api', 'impl'"

        when:
        IdeaProject project = withConnection { connection -> connection.action(new FetchIdeaModel()).run() }
        def module = project.children.find { it.name == 'impl' }

        then:
        def libs = module.dependencies
        IdeaSingleEntryLibraryDependency lib = libs.find {it instanceof IdeaSingleEntryLibraryDependency}

        lib.file.exists()
        lib.file.path.endsWith('coolLib-1.0.jar')

        lib.source.exists()
        lib.source.path.endsWith('coolLib-1.0-sources.jar')

        lib.javadoc.exists()
        lib.javadoc.path.endsWith('coolLib-1.0-javadoc.jar')

        lib.scope.scope == 'TEST'

        IdeaModuleDependency mod = libs.find {it instanceof IdeaModuleDependency}
        mod.targetModuleName == 'api'
        if (targetVersion >= GradleVersion.version("3.4")) {
            mod.scope.scope == 'PROVIDED'
        } else {
            mod.scope.scope == 'COMPILE'
        }
    }
}
