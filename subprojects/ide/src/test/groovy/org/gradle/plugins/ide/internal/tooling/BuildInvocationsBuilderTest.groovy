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
import org.gradle.api.internal.project.DefaultProjectTaskLister
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.util.TestUtil
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class BuildInvocationsBuilderTest extends Specification {
    @Shared
    def project = TestUtil.builder().withName("root").build()
    @Shared
    def child1 = TestUtil.builder().withName("child1").withParent(project).build()
    @Shared
    def child2 = TestUtil.builder().withName("child2").withParent(project).build()
    @Shared
    def grandChildOfChild1 = TestUtil.builder().withName("grandChild").withParent(child1).build()

    def setupSpec() {
        // create a project/task tree:
        //   root (t1, t2)
        //   +--- child 1 (t2, t3)
        //        +-- grand child (t3, t4)
        //   +-- child 2 (t2)

        // root tasks (one public, one private)
        def task1OfRoot = project.tasks.create('t1', DefaultTask)
        task1OfRoot.group = 'build'
        task1OfRoot.description = 'T1 from root'

        def task2OfRoot = project.tasks.create('t2', DefaultTask)
        task2OfRoot.group = null
        task2OfRoot.description = null

        // child tasks (one public, one private)
        def task1OfChild1 = child1.tasks.create('t2', DefaultTask)
        task1OfChild1.group = 'build'
        task1OfChild1.description = 'T2 from child 1'

        def task2OfChild1 = child1.tasks.create('t3', DefaultTask)
        task2OfChild1.group = null
        task2OfChild1.description = 'T3 from child'

        def task1OfChild2 = child2.tasks.create('t2', DefaultTask)
        task1OfChild2.group = 'build'
        task1OfChild2.description = 'T2 from child 2'

        // grand child tasks (one public, one private)
        def task1OfGrandChild = grandChildOfChild1.tasks.create('t3', DefaultTask)
        task1OfGrandChild.group = 'build'
        task1OfGrandChild.description = null

        def task2OfGrandChild = grandChildOfChild1.tasks.create('t4', DefaultTask)
        task2OfGrandChild.group = null
        task2OfGrandChild.description = 'T4 from grand child'
    }

    def "canBuild"() {
        given:
        def builder = new BuildInvocationsBuilder(new DefaultProjectTaskLister())

        when:
        def canBuild = builder.canBuild(BuildInvocations.name)

        then:
        canBuild
    }

    @Unroll("tasks and selectors for #startProject")
    def "tasksAndSelectorsAndTheirVisibility"() {
        given:
        def builder = new BuildInvocationsBuilder(new DefaultProjectTaskLister())

        when:
        def model = builder.buildAll("org.gradle.tooling.model.gradle.BuildInvocations", startProject)

        then:
        model.taskSelectors*.projectPath as Set == [startProject.path] as Set
        model.taskSelectors*.name as Set == selectorNames as Set
        model.tasks*.name as Set == taskNames as Set

        model.taskSelectors.findAll { it.public }*.name as Set == visibleSelectors as Set
        model.tasks.findAll { it.public }*.name as Set == visibleTasks as Set

        where:
        startProject       | selectorNames            | taskNames     | visibleSelectors   | visibleTasks
        project            | ['t1', 't2', 't3', 't4'] | ['t1', 't2']  | ['t1', 't2', 't3'] | ['t1']
        child1             | ['t2', 't3', 't4']       | ['t2', 't3',] | ['t2', 't3']       | ['t2']
        child2             | ['t2']                   | ['t2']        | ['t2']             | ['t2']
        grandChildOfChild1 | ['t3', 't4']             | ['t3', 't4',] | ['t3']             | ['t3']
    }

    def "implicitProjectFlagWins"() {
        given:
        def builder = new BuildInvocationsBuilder(new DefaultProjectTaskLister())

        when:
        def model = builder.buildAll("org.gradle.tooling.model.gradle.BuildInvocations", child1, true)

        then:
        model.taskSelectors*.name as Set == ['t1', 't2', 't3', 't4'] as Set
        model.taskSelectors.each { it ->
            assert it.projectPath == ':'
        }
    }

    def "nonNullDescriptionFromSmallestProjectPathAlwaysWins"() {
        given:
        def builder = new BuildInvocationsBuilder(new DefaultProjectTaskLister())

        when:
        def model = builder.buildAll("org.gradle.tooling.model.gradle.BuildInvocations", project)

        then:
        assert model.taskSelectors.find { it.name == 't1' }.description == 'T1 from root'
        assert model.taskSelectors.find { it.name == 't2' }.description == ''
        assert model.taskSelectors.find { it.name == 't3' }.description == 'T3 from child'
        assert model.taskSelectors.find { it.name == 't4' }.description == 'T4 from grand child'
    }

}
