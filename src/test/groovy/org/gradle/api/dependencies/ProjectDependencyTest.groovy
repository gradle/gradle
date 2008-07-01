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

package org.gradle.api.dependencies

import java.awt.Point
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.internal.dependencies.DefaultDependencyManager
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.UnknownDependencyNotation
import org.gradle.util.HelperUtil

/**
* @author Hans Dockter
*/
class ProjectDependencyTest extends GroovyTestCase {
    static final String TEST_CONF = "conf"
    static final Set TEST_CONF_SET = [TEST_CONF]
    static final String TEST_DEPENDENCY_CONF = "depconf"

    ProjectDependency projectDependency
    DefaultProject project
    DefaultProject dependencyProject
    ModuleRevisionId dependencyProjectModuleRevisionId
    String dependencyProjectArtifactProductionTaskName

    void setUp() {
        project = HelperUtil.createRootProject(new File('root'))

        dependencyProjectModuleRevisionId = new ModuleRevisionId(new ModuleId('org', 'otherproject'), '1.0')
        dependencyProjectArtifactProductionTaskName = 'somename'
        DefaultDependencyManager mockDependencyManager = [createModuleRevisionId: {dependencyProjectModuleRevisionId},
                getArtifactProductionTaskName: {dependencyProjectArtifactProductionTaskName}] as DefaultDependencyManager
        dependencyProject = HelperUtil.createRootProject(new File('dependency'))
        dependencyProject.dependencies = mockDependencyManager
        dependencyProject.createTask(dependencyProjectArtifactProductionTaskName)

        projectDependency = new ProjectDependency(TEST_CONF_SET, dependencyProject, project)
    }

    void testProjectDependencyStringObjectProject() {
        assertEquals(Dependency.DEFAULT_CONFIGURATION, projectDependency.dependencyConfiguration)
        assertEquals(TEST_CONF_SET, projectDependency.confs)
        assert dependencyProject.is(projectDependency.userDependencyDescription)
        assertEquals(project, projectDependency.project)
    }

    void testProjectDependencyProjectString() {
        projectDependency = new ProjectDependency(dependencyProject, TEST_DEPENDENCY_CONF)
        assertEquals(TEST_DEPENDENCY_CONF, projectDependency.dependencyConfiguration)
        assert projectDependency.userDependencyDescription.is(dependencyProject)
        assert !projectDependency.confs
        assertNull projectDependency.project
    }

    void testValidation() {
        shouldFail(UnknownDependencyNotation) {
            new ProjectDependency(TEST_CONF_SET, "string", project)
        }
        shouldFail(UnknownDependencyNotation) {
            new ProjectDependency(TEST_CONF_SET, new Point(3, 4), project)
        }
    }

    void testCreateDependencyDescriptor() {
        projectDependency.dependencyConfiguration = TEST_DEPENDENCY_CONF
        DependencyDescriptor dependencyDescriptor = projectDependency.createDepencencyDescriptor()
        assertEquals(dependencyProjectModuleRevisionId, dependencyDescriptor.dependencyRevisionId)
        assertEquals(1, dependencyDescriptor.getDependencyConfigurations(TEST_CONF).size())
        assertEquals(TEST_DEPENDENCY_CONF, dependencyDescriptor.getDependencyConfigurations(TEST_CONF)[0])
        assertTrue(dependencyDescriptor.isChanging())
    }

    void testInitialize() {
       project.dependencies.addConf2Tasks(TEST_CONF, TEST_CONF)
       project.createTask(TEST_CONF)
       projectDependency.initialize()
       assert project.task(TEST_CONF).dependsOn.contains(Project.PATH_SEPARATOR + "$dependencyProjectArtifactProductionTaskName" as String)
    }
}
