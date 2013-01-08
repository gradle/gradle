/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution

import spock.lang.Specification
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder

/**
 * by Szczepan Faber, created at: 1/8/13
 */
class ProjectFinderByTaskPathTest extends Specification {

    def finder = new ProjectFinderByTaskPath()

    //root->foo->bar->baz
    ProjectInternal root = new ProjectBuilder().withName("root").build()
    ProjectInternal foo = new ProjectBuilder().withName("foo").withParent(root).build()
    ProjectInternal bar = new ProjectBuilder().withName("bar").withParent(foo).build()

    def "finds root"() {
        expect:
        finder.findProject(":", root) == root
        finder.findProject(":", foo) == root
        finder.findProject(":", bar) == root
    }

    def "finds project from root"() {
        expect:
        finder.findProject(":foo:bar:someTask", foo) == bar
        finder.findProject(":foo:bar:someTask", bar) == bar
        finder.findProject(":foo:bar:someTask", root) == bar
        finder.findProject(":foo:someTask", bar) == foo
        finder.findProject(":someTask", bar) == root
    }

    def "finds project relatively"() {
        expect:
        finder.findProject("foo:bar:someTask", root) == bar
        finder.findProject("foo:someTask", root) == foo
        finder.findProject("bar:someTask", foo) == bar
    }

    def "provides decent information when project cannot be found"() {
        when:
        finder.findProject("foo:xxx:someTask", root)
        then:
        def ex = thrown(ProjectFinderByTaskPath.ProjectLookupException)
        ex.message.contains("xxx")
    }

    def "only allows valid task path as input"() {
        when:
        finder.findProject("someTaskName", foo)
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('someTaskName')
    }
}
