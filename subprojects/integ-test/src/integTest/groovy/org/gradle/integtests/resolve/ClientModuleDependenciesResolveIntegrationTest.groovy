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


import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.IvyRepository
import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec
import org.junit.Rule
import org.junit.Test

/**
 * @author Hans Dockter
 */
public class ClientModuleDependenciesResolveIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()

    @Test
    public void testResolve() {
        // the actual testing is done in the build script.
        File projectDir = new File(distribution.getSamplesDir(), "clientModuleDependencies/shared");
        executer.inDirectory(projectDir).withTasks("testDeps").run();

        projectDir = new File(distribution.getSamplesDir(), "clientModuleDependencies/api");
        executer.inDirectory(projectDir).withTasks("testDeps").run();
    }

    @Test
    public void "uses metadata from Client Module and looks up artifact in declared repositories"() {
        given:
        def repo = ivyRepo()
        def projectA = repo.module('group', 'projectA', '1.2')
        def projectB = repo.module('group', 'projectB', '1.3')
        projectA.publish()
        projectB.publish()

        server.start()

        and:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
    ivy { url "http://localhost:${server.port}/repo2" }
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
        server.expectGet('/repo/group/projectB/1.3/ivy-1.3.xml', projectB.ivyFile)
        server.expectGetMissing('/repo/group/projectA/1.2/projectA-1.2.jar')
        server.expectGet('/repo2/group/projectA/1.2/projectA-1.2.jar', projectA.jarFile)
        server.expectGet('/repo/group/projectB/1.3/projectB-1.3.jar', projectB.jarFile)

        expect:
        succeeds('listJars')

//        given:
        server.resetExpectations()
        // TODO - should not require this - artifact has been cached
        server.expectGetMissing('/repo/group/projectA/1.2/projectA-1.2.jar')

//        expect:
        succeeds('listJars')
    }

    IvyRepository ivyRepo() {
        return new IvyRepository(file('ivy-repo'))
    }

}