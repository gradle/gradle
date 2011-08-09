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
package org.gradle.integtests

import org.gradle.api.Project
import org.gradle.api.artifacts.LenientConfiguration
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.specs.Specs
import org.gradle.integtests.fixtures.MavenRepository
import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest
import org.gradle.util.HelperUtil
import org.junit.Before
import org.junit.Test

public class ResolvedConfigurationIntegrationTest extends AbstractIntegrationTest {

    def DefaultProject project = HelperUtil.createRootProject()
    def Project childProject = HelperUtil.createChildProject(project, "child", new File("."))
    def File repo = testFile('repo')

    @Before
    public void boringSetup() {
        project.allprojects { apply plugin: 'java' }

        project.repositories {
            mavenRepo urls: repo.toURI()
        }
    }

    @Test
    public void "resolves leniently"() {
        publishArtifact('org.foo', 'hiphop')
        publishArtifact('org.foo', 'rock', "some unresolved dependency")

        project.dependencies {
            compile 'org.foo:hiphop:1.0'
            compile 'org.foo:hiphopxx:1.0' //does not exist
            compile childProject

            compile 'org.foo:rock:1.0' //contains unresolved transitive dependency
        }

        LenientConfiguration compile = project.configurations.compile.resolvedConfiguration.lenientConfiguration

        def unresolved = compile.getUnresolvedModuleDependencies()
        def resolved = compile.getFirstLevelModuleDependencies(Specs.SATISFIES_ALL)

        assert resolved.size() == 3
        assert resolved.find { it.moduleName == 'hiphop' }
        assert resolved.find { it.moduleName == 'rock' }
        assert resolved.find { it.moduleName == 'child' }

        assert unresolved.size() == 2
        assert unresolved.find { it.id.contains 'hiphopxx' }
        assert unresolved.find { it.id.contains 'some unresolved dependency' }
    }

    @Test
    public void "resolves leniently from mixed confs"() {
        publishArtifact('org.foo', 'hiphop')
        publishArtifact('org.foo', 'rock', "some unresolved dependency")

        project.allprojects { apply plugin: 'java' }

        project.repositories {
            mavenRepo urls: repo.toURI()
        }

        project.configurations {
            someConf
        }

        project.dependencies {
            compile 'org.foo:hiphop:1.0'
            someConf 'org.foo:hiphopxx:1.0' //does not exist
        }

        LenientConfiguration compile = project.configurations.compile.resolvedConfiguration.lenientConfiguration

        def unresolved = compile.getUnresolvedModuleDependencies()
        def resolved = compile.getFirstLevelModuleDependencies(Specs.SATISFIES_ALL)

        assert resolved.size() == 1
        assert resolved.find { it.moduleName == 'hiphop' }
        assert unresolved.size() == 0

        LenientConfiguration someConf = project.configurations.someConf.resolvedConfiguration.lenientConfiguration

        unresolved = someConf.getUnresolvedModuleDependencies()
        resolved = someConf.getFirstLevelModuleDependencies(Specs.SATISFIES_ALL)

        assert resolved.size() == 0
        assert unresolved.size() == 1
        assert unresolved.find { it.id.contains 'hiphopxx' }
    }

    protected File publishArtifact(group, artifact, dependency = null, classifier = null) {
        //TODO SF - duplicated in few places
        def module = new MavenRepository(repo).module(group, artifact, 1.0, classifier)
        if (dependency) {
            module.dependsOn(dependency)
        }
        return module.publishArtifact()
    }
}
