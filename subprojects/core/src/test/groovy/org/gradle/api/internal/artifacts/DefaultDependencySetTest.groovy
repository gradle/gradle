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
package org.gradle.api.internal.artifacts

import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.tasks.TaskDependency
import spock.lang.Specification

class DefaultDependencySetTest extends Specification {
    final DefaultDomainObjectSet<Dependency> store = new DefaultDomainObjectSet<Dependency>(Dependency)
    final DefaultDependencySet set = new DefaultDependencySet('dependencies', Mock(Configuration), store)

    def "set is built by the union of tasks that build the self-resolving dependencies in the set"() {
        SelfResolvingDependency dep1 = Mock()
        SelfResolvingDependency dep2 = Mock()
        Task task1 = Mock()
        Task task2 = Mock()
        Task task3 = Mock()
        Dependency ignored = Mock()

        given:
        store.add(dep1)
        store.add(dep2)
        store.add(ignored)
        builtBy(dep1, task1, task2)
        builtBy(dep2, task1, task3)

        expect:
        set.buildDependencies.getDependencies(null) == [task1, task2, task3] as Set
    }

    def builtBy(SelfResolvingDependency dependency, Task... tasks) {
        TaskDependency taskDependency = Mock()
        _ * dependency.buildDependencies >> taskDependency
        _ * taskDependency.getDependencies(_) >> (tasks as Set)
    }
}
