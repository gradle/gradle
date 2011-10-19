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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.IvyRepository
import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec

class IvyLocalDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {
    public void "does not cache local artifacts or metadata"() {
        distribution.requireOwnUserHomeDir()

        given:
        def repo = ivyRepo()
        def moduleA = repo.module('group', 'projectA', '1.2')
        moduleA.publish()
        def moduleB = repo.module('group', 'projectB', '9-beta')
        moduleB.publish()

        and:
        buildFile << """
repositories {
    ivy {
        artifactPattern "${repo.uri}/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        when:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar')
        file('libs/projectA-1.2.jar').assertIsCopyOf(moduleA.jarFile)

        when:
        moduleA.dependsOn('group', 'projectB', '9-beta')
        moduleA.publishWithChangedContent()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-9-beta.jar')
        file('libs/projectA-1.2.jar').assertIsCopyOf(moduleA.jarFile)
        file('libs/projectB-9-beta.jar').assertIsCopyOf(moduleB.jarFile)
    }

    public void "uses latest version for version range and latest_integration"() {
        distribution.requireOwnUserHomeDir()
        def repo = ivyRepo()

        given:
        buildFile << """
repositories {
    ivy {
        artifactPattern "${repo.uri}/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]"
    }
}

configurations {
    compile
}

dependencies {
    compile group: "group", name: "projectA", version: "1.+"
    compile group: "group", name: "projectB", version: "latest.integration"
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        when:
        def projectA1 = repo.module("group", "projectA", "1.1")
        projectA1.publish()
        def projectB1 = repo.module("group", "projectB", "1.0")
        projectB1.publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar', 'projectB-1.0.jar')
        file('libs/projectA-1.1.jar').assertIsCopyOf(projectA1.jarFile)
        file('libs/projectB-1.0.jar').assertIsCopyOf(projectB1.jarFile)

        when:
        def projectA2 = repo.module("group", "projectA", "1.2")
        projectA2.publish()
        def projectB2 = repo.module("group", "projectB", "2.0")
        projectB2.publish()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-2.0.jar')
        file('libs/projectA-1.2.jar').assertIsCopyOf(projectA2.jarFile)
        file('libs/projectB-2.0.jar').assertIsCopyOf(projectB2.jarFile)
    }

    IvyRepository ivyRepo() {
        return new IvyRepository(file('ivy-repo'))
    }
}
