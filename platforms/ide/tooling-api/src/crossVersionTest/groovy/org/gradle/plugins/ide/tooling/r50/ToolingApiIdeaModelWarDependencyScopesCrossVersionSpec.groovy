/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r50

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency

// The war plugin's providedCompile / providedRuntime configurations map to the IDEA PROVIDED scope. This mapping
// is surfaced through IdeaSingleEntryLibraryDependency.getScope() but is not asserted by any other cross-version
// spec (nor by a unit test).
@TargetGradleVersion(">=5.0")
class ToolingApiIdeaModelWarDependencyScopesCrossVersionSpec extends ToolingApiSpecification {

    def "war #configuration dependencies are mapped to the IDEA PROVIDED scope"() {
        setup:
        def mavenRepo = new MavenFileRepository(file("maven-repo"))
        mavenRepo.module('org.gradle.test', 'foo', '1.0').publish()

        buildFile << """
            apply plugin: 'war'
            apply plugin: 'idea'

            repositories {
                maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                ${configuration} 'org.gradle.test:foo:1.0'
            }
        """

        when:
        IdeaProject project = loadToolingModel(IdeaProject)
        IdeaModule module = project.modules[0]
        def libraries = module.dependencies.findAll { it instanceof IdeaSingleEntryLibraryDependency }

        then:
        libraries.size() == 1
        libraries[0].file.path.endsWith('foo-1.0.jar')
        libraries[0].scope.scope == 'PROVIDED'

        where:
        configuration << ['providedCompile', 'providedRuntime']
    }
}
