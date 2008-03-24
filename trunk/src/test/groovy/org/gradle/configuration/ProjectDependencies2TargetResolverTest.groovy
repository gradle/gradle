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

package org.gradle.configuration

import org.gradle.api.internal.DefaultTask
import org.gradle.api.internal.dependencies.DefaultDependencyManager
import org.gradle.api.internal.dependencies.DefaultDependencyManagerFactory
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.ProjectFactory

/**
* @author Hans Dockter
*/
class ProjectDependencies2TargetResolverTest extends GroovyTestCase {

    void testExecuteWithSubBuildAndProjectDependencies() {
        DefaultProject root = new DefaultProject("root", null, new File(""), null, new ProjectFactory(new DefaultDependencyManagerFactory(new File('root'))), new DefaultDependencyManager(), null, null, null)
        DefaultProject child = root.addChildProject("child")
        DefaultTask rootTarget = new DefaultTask(root, 'compile')
        DefaultTask childTarget = new DefaultTask(child, 'compile')
        root.tasks = [(rootTarget.name): rootTarget]
        child.tasks = [(childTarget.name): childTarget]
        child.dependsOn(root.path, false)

        new ProjectDependencies2TasksResolver().resolve(root)

        assertEquals([rootTarget.path] as Set, childTarget.dependsOn)
    }
}