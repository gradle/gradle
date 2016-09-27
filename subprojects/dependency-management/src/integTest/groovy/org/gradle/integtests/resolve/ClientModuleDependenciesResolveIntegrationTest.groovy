/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.file.LeaksFileHandles

@LeaksFileHandles
public class ClientModuleDependenciesResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    public void "uses metadata from Client Module and looks up artifact in declared repositories"() {
        given:
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = mavenHttpRepo("repo2")
        def projectAInRepo1 = repo1.module('group', 'projectA', '1.2')
        def projectAInRepo2 = repo2.module('group', 'projectA', '1.2').publish()
        def projectB = repo1.module('group', 'projectB', '1.3').publish()

        and:
        buildFile << """
repositories {
    ivy { url "${repo1.uri}" }
    maven { url "${repo2.uri}" }
}
configurations { compile }
dependencies {
    compile module("group:projectA:1.2") {
       dependency("group:projectB:1.3")
    }
}
task listJars {
    doLast {
        assert configurations.compile.collect { it.name } == ['projectA-1.2.jar', 'projectB-1.3.jar']
    }
}
"""

        when:
        projectB.ivy.expectGet()
        projectB.jar.expectGet()
        projectAInRepo1.ivy.expectGetMissing()
        projectAInRepo1.jar.expectHeadMissing()
        projectAInRepo2.pom.expectGet()
        projectAInRepo2.artifact.expectGet()

        then:
        succeeds('listJars')

        when:
        server.resetExpectations()

        then:
        succeeds('listJars')
    }

    def "client module dependency ignores published artifact listing and resolves single jar file"() {
        def projectA = ivyHttpRepo.module('group', 'projectA', '1.2')
                .artifact()
                .artifact(classifier: "extra")
                .publish()

        buildFile << """
repositories {
    ivy { url "${ivyHttpRepo.uri}" }
}
configurations {
    regular
    clientModule
}
dependencies {
    regular "group:projectA:1.2"
    clientModule module("group:projectA:1.2")
}
task listJars {
    doLast {
        assert configurations.regular.collect { it.name } == ['projectA-1.2.jar', 'projectA-1.2-extra.jar']
    }
}
task listClientModuleJars {
    doLast {
        assert configurations.clientModule.collect { it.name } == ['projectA-1.2.jar']
    }
}
"""

        when:
        projectA.ivy.expectGet()
        projectA.jar.expectGet()

        then:
        succeeds('listClientModuleJars')

        when:
        server.resetExpectations()
        projectA.getArtifact(classifier: "extra").expectGet()

        then:
        succeeds('listJars')

        when:
        server.resetExpectations()

        then:
        succeeds('listClientModuleJars')
    }
}
