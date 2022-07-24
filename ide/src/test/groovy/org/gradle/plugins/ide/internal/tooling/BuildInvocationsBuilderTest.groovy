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
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.ClassRule
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@UsesNativeServices
@CleanupTestDirectory
class BuildInvocationsBuilderTest extends Specification {
    @Shared
    @ClassRule
    public TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())
    @Shared
    def project = TestUtil.builder(temporaryFolder).withName("root").build()
    @Shared
    def child = ProjectBuilder.builder().withName("child").withParent(project).build()
    @Shared
    def grandChild1OfChild = ProjectBuilder.builder().withName("grandChild1").withParent(child).build()
    @Shared
    def grandChild2OfChild = ProjectBuilder.builder().withName("grandChild2").withParent(child).build()

    def setupSpec() {
        // create a project/task tree:
        //   root (t1, t2)
        //   +--- child 1 (t2, t3)
        //        +-- grand child (t3, t4)
        //        +-- grand child (t4)

        // root tasks (one public, one private)
        def task1OfRoot = project.tasks.create('t1', DefaultTask)
        task1OfRoot.group = 'build'
        task1OfRoot.description = 'T1 from root'

        def task2OfRoot = project.tasks.create('t2', DefaultTask)
        task2OfRoot.group = null
        task2OfRoot.description = null

        // child tasks (one public, one private)
        def task1OfChild1 = child.tasks.create('t2', DefaultTask)
        task1OfChild1.group = 'build'
        task1OfChild1.description = 'T2 from child'

        def task2OfChild1 = child.tasks.create('t3', DefaultTask)
        task2OfChild1.group = null
        task2OfChild1.description = 'T3 from child'

        // grand child tasks (one public, one private)
        def task1OfGrandChild = grandChild1OfChild.tasks.create('t3', DefaultTask)
        task1OfGrandChild.group = 'build'
        task1OfGrandChild.description = null

        def task2OfGrandChild = grandChild1OfChild.tasks.create('t4', DefaultTask)
        task2OfGrandChild.group = null
        task2OfGrandChild.description = 'T4 from grand child 1'

        def taskOfGrandChild2 = grandChild2OfChild.tasks.create('t4', DefaultTask)
        taskOfGrandChild2.group = null
        taskOfGrandChild2.description = 'T4 from grand child 2'
    }

    def "BuildInvocations model is accepted"() {
        given:
        def builder = new BuildInvocationsBuilder(new DefaultProjectTaskLister())

        when:
        def canBuild = builder.canBuild(BuildInvocations.name)

        then:
        canBuild
    }

    @Unroll("tasks and selectors for #startProject")
    def "BuildInvocations model is created from tasks and task selectors for given project and its subprojects"() {
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
        child              | ['t2', 't3', 't4']       | ['t2', 't3',] | ['t2', 't3']       | ['t2']
        grandChild1OfChild | ['t3', 't4']             | ['t3', 't4',] | ['t3']             | ['t3']
        grandChild2OfChild | ['t4']                   | ['t4',]       | []                 | []
    }

    def "TaskSelector description is taken from task that TaskNameComparator considers to be of lowest ordering"() {
        given:
        def builder = new BuildInvocationsBuilder(new DefaultProjectTaskLister())

        when:
        def model = builder.buildAll("org.gradle.tooling.model.gradle.BuildInvocations", project)

        then:
        assert model.taskSelectors.find { it.name == 't1' }.description == 'T1 from root'
        assert model.taskSelectors.find { it.name == 't2' }.description == null
        assert model.taskSelectors.find { it.name == 't3' }.description == 'T3 from child'
        assert model.taskSelectors.find { it.name == 't4' }.description == 'T4 from grand child 1'
    }

}
