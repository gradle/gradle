/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.configuration.project

import org.gradle.util.HelperUtil
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
class ProjectDependencies2TaskResolverTest extends Specification {
    private root = HelperUtil.createRootProject()
    private child = HelperUtil.createChildProject(root, "child")
    private rootTask = root.tasks.create('compile')
    private childTask = child.tasks.create('compile')

    private resolver = new ProjectDependencies2TaskResolver()

    void "resolves task dependencies"() {
        child.dependsOn(root.path, false)
        when:
        resolver.execute(child)
        then:
        childTask.taskDependencies.getDependencies(childTask) == [rootTask] as Set
    }
}
