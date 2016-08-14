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

package org.gradle.execution.taskpath

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class ProjectFinderByTaskPathTest extends AbstractProjectBuilderSpec {

    def finder = new ProjectFinderByTaskPath()

    //root->foo->bar
    ProjectInternal root = TestUtil.create(temporaryFolder).rootProject()
    ProjectInternal foo = TestUtil.createChildProject(root, "foo")
    ProjectInternal bar = TestUtil.createChildProject(foo, "bar")

    def "finds root"() {
        expect:
        finder.findProject(":", root) == root
        finder.findProject(":", foo) == root
        finder.findProject(":", bar) == root
    }

    def "finds project from root"() {
        expect:
        finder.findProject(":foo:bar", foo) == bar
        finder.findProject(":foo:bar", bar) == bar
        finder.findProject(":foo:bar", root) == bar
        finder.findProject(":foo", bar) == foo
    }

    def "finds project relatively"() {
        expect:
        finder.findProject("foo:bar", root) == bar
        finder.findProject("foo", root) == foo
        finder.findProject("bar", foo) == bar
    }

    def "provides decent information when project cannot be found"() {
        when:
        finder.findProject("foo:xxx", root)
        then:
        def ex = thrown(ProjectFinderByTaskPath.ProjectLookupException)
        ex.message.contains("xxx")
    }
}
