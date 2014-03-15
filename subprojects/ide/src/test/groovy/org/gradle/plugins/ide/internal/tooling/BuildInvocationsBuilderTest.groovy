/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling

import org.gradle.api.DefaultTask
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.tooling.internal.gradle.DefaultGradleTaskSelector
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.util.TestUtil
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class BuildInvocationsBuilderTest extends Specification {
    def publicationRegistry = Mock(ProjectPublicationRegistry)
    def builder = new BuildInvocationsBuilder(new GradleProjectBuilder(publicationRegistry))
    @Shared def project = TestUtil.builder().withName("root").build()
    @Shared def child1 = TestUtil.builder().withName("child1").withParent(project).build()
    @Shared def child1a = TestUtil.builder().withName("child1a").withParent(child1).build()
    @Shared def child1b = TestUtil.builder().withName("child1b").withParent(child1).build()

    def setupSpec() {
        child1a.tasks.create('t1', DefaultTask)
        child1b.tasks.create('t1', DefaultTask)
        child1b.tasks.create('t2', DefaultTask)
        child1.tasks.create('t2', DefaultTask)
        project.tasks.create('t3', DefaultTask)
    }

    def setup() {
        publicationRegistry.getPublications(_) >> Collections.emptySet()
    }

    def "can build model"() {
        expect:
        builder.canBuild(BuildInvocations.name)
    }

    @Unroll("builds model for #startProject")
    def "builds model"() {
        expect:
        def model = builder.buildAll("org.gradle.tooling.model.gradle.BuildInvocations", startProject)
        model.taskSelectors*.name as Set == selectorNames as Set
        model.taskSelectors.find { it.name == 't1' }?.tasks == t1Tasks as Set

        where:
        startProject | selectorNames       | t1Tasks
        project      | ['t1', 't2', 't3']  | [':child1:child1a:t1', ':child1:child1b:t1']
        child1       | ['t1', 't2']        | [':child1:child1a:t1', ':child1:child1b:t1']
        child1a      | ['t1']              | [':child1:child1a:t1']
    }

    def "builds recursive model"() {
        when:
        def model = builder.buildAll("org.gradle.tooling.model.gradle.BuildInvocations", project, true)

        then:
        model.taskSelectors.find { DefaultGradleTaskSelector it ->
            it.name == 't1' && it.description.startsWith("t1")
        }?.tasks == [':child1:child1a:t1', ':child1:child1b:t1'] as Set
        model.taskSelectors.find { DefaultGradleTaskSelector it ->
            it.name == 't1' && it.description.startsWith(":child1:t1")
        }?.tasks == [':child1:child1a:t1', ':child1:child1b:t1'] as Set
        model.taskSelectors.find { DefaultGradleTaskSelector it ->
            it.name == 't1' && it.description.startsWith(":child1:child1a:t1")
        }?.tasks == [':child1:child1a:t1'] as Set
        model.taskSelectors*.name.each { it != null }
        model.taskSelectors*.description.each { it != null }
        model.taskSelectors*.displayName.each { it != null }
    }
}
