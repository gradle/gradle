/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.platform.base.internal
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.internal.AbstractBuildableComponentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class BuildableComponentSpecTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def element = new TestBuildableComponentSpec(Stub(ComponentSpecIdentifier))
    def dependedOn1 = Stub(Task)
    def dependedOn2 = Stub(Task)
    def lifecycleTask = TestUtil.create(tmpDir).task(DefaultTask)

    def "has direct dependencies with no lifecycle task set"() {
        when:
        element.builtBy(dependedOn1, dependedOn2)

        then:
        element.getBuildDependencies().getDependencies(Stub(Task)) == [dependedOn1, dependedOn2] as Set
    }

    def "has intervening lifecycle task as dependency when set"() {
        when:
        element.builtBy(dependedOn1)
        element.setBuildTask(lifecycleTask)
        element.builtBy(dependedOn2)

        then:
        element.getBuildDependencies().getDependencies(Stub(Task)) == [lifecycleTask] as Set

        and:
        lifecycleTask.getTaskDependencies().getDependencies(lifecycleTask) == [dependedOn1, dependedOn2] as Set
    }

    class TestBuildableComponentSpec extends AbstractBuildableComponentSpec {
        TestBuildableComponentSpec(ComponentSpecIdentifier identifier) {
            super(identifier, TestBuildableComponentSpec)
        }
    }
}
