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
package org.gradle.integtests.resolve;


import org.junit.Test

/**
 * @author Hans Dockter
 */
public class ClientModuleDependenciesResolveIntegrationTest extends AbstractDependencyResolutionTest {
    @Test
    public void testResolve() {
        when:
        // the actual testing is done in the build script.
        File projectDir = new File(distribution.getSamplesDir(), "clientModuleDependencies/shared");
        then:
        executer.inDirectory(projectDir).withTasks("testDeps").run();

        when:
        projectDir = new File(distribution.getSamplesDir(), "clientModuleDependencies/api");
        then:
        executer.inDirectory(projectDir).withTasks("testDeps").run();
    }

    @Test
    public void "uses metadata from Client Module and looks up artifact in declared repositories"() {
        given:
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = ivyHttpRepo("repo2")
        def projectAInRepo1 = repo1.module('group', 'projectA', '1.2')
        def projectAInRepo2 = repo2.module('group', 'projectA', '1.2').publish()
        def projectB = repo1.module('group', 'projectB', '1.3').publish()

        server.start()

        and:
        buildFile << """
repositories {
    ivy { url "${repo1.uri}" }
    ivy { url "${repo2.uri}" }
}
configurations { compile }
dependencies {
    compile module("group:projectA:1.2") {
       dependency("group:projectB:1.3")
    }
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar', 'projectB-1.3.jar']
}
"""

        when:
        projectB.expectIvyGet()
        projectB.expectJarGet()
        projectAInRepo1.expectIvyGetMissing()
        projectAInRepo1.expectJarHeadMissing()
        projectAInRepo2.expectIvyGet()
        projectAInRepo2.expectJarGet()

        then:
        succeeds('listJars')

        when:
        server.resetExpectations()

        then:
        succeeds('listJars')
    }
}