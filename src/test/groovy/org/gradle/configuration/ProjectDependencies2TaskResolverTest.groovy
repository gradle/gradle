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
import org.gradle.api.internal.project.DefaultProject
import org.gradle.util.HelperUtil
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test;

/**
 * @author Hans Dockter
 */
class ProjectDependencies2TaskResolverTest {
    DefaultProject root
    DefaultProject child
    DefaultTask rootTask
    DefaultTask childTask
    ProjectDependencies2TaskResolver resolver

    @Before public void setUp()  {
        resolver = new ProjectDependencies2TaskResolver()
        root = HelperUtil.createRootProject(new File('rootDir'))
        child = HelperUtil.createChildProject(root, "child")
        rootTask = root.createTask('compile')
        childTask = child.createTask('compile')
    }

    @Test public void testResolve() {
        child.dependsOn(root.path, false)
        resolver.resolve(root)
        assertEquals([rootTask] as Set, childTask.dependsOn)
    }
}