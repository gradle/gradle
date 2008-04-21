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

package org.gradle.api.tasks.bundling

import org.gradle.api.Task
import org.gradle.api.plugins.JavaConvention
import org.gradle.api.tasks.AbstractConventionTaskTest
import org.gradle.api.tasks.AbstractTaskTest

/**
 * @author Hans Dockter
 */
class BundleTest extends AbstractConventionTaskTest {
    Bundle bundle

    ArchiveType testArchiveType

    Map testConventionMapping

    String testDefaultSuffix

    String testTasksBaseName

    List testChildrenDependsOn

    String expectedArchiveName

    String customTaskName

    String expectedDefaultArchiveName

    Task getTask() {bundle}

    void setUp() {
        super.setUp()
        testChildrenDependsOn = ['othertaskpath', 'othertaskpath2']
        bundle = new Bundle(project, AbstractTaskTest.TEST_TASK_NAME)
        bundle.childrenDependOn = testChildrenDependsOn
        bundle.tasksBaseName = 'testbasename'
        bundle.defaultArchiveTypes = JavaConvention.DEFAULT_ARCHIVE_TYPES
        customTaskName = 'customtaskname'
        expectedArchiveName = "${testTasksBaseName}_${testDefaultSuffix}"
        expectedDefaultArchiveName = "${testTasksBaseName}_${testDefaultSuffix}"
        testArchiveType = new ArchiveType('suf', [:], TestArchiveTask)

    }

    void testBundle() {
        bundle = new Bundle(project, AbstractTaskTest.TEST_TASK_NAME)
        assertEquals([] as Set, bundle.childrenDependOn)
    }

    void testJarWithDefaultValues() {
        Jar jar = checkForDefaultValues(bundle.jar(), bundle.defaultArchiveTypes.jar)
    }

    void testZipWithDefaultValues() {
        Zip zip = checkForDefaultValues(bundle.zip(), bundle.defaultArchiveTypes.zip)
    }

    void testWarWithDefaultValues() {
        War war = checkForDefaultValues(bundle.war(), bundle.defaultArchiveTypes.war)
    }

    void testTarWithDefaultValues() {
        Tar tar = checkForDefaultValues(bundle.tar(), bundle.defaultArchiveTypes.tar)
    }

    void testTarGzWithDefaultValues() {
        Tar tar = checkForDefaultValues(bundle.tarGz(), bundle.defaultArchiveTypes['tar.gz'])
    }

    void testTarBzip2WithDefaultValues() {
        Tar tar = checkForDefaultValues(bundle.tarBzip2(), bundle.defaultArchiveTypes['tar.bzip2'])
    }

    void testCreateArchiveWithDefaultValues() {
        TestArchiveTask testTask = checkForDefaultValues(bundle.createArchive(testArchiveType), testArchiveType)
    }

    void testCreateArchiveWithCustomName() {
        TestArchiveTask testTask = bundle.createArchive(testArchiveType, customTaskName)
        checkCommonStuff(testTask, "${customTaskName}_${testArchiveType.defaultExtension}", testArchiveType.conventionMapping, customTaskName)
    }

    void testCreateMultipleArchives() {
        AbstractArchiveTask task1 = bundle.zip('zip1')
        AbstractArchiveTask task2 = bundle.zip('zip2')
        assertEquals(testChildrenDependsOn as Set, task1.dependsOn)
        assertEquals(testChildrenDependsOn as Set, task2.dependsOn)
    }

    void testCreateArchiveWithConfigureClosure() {
        Closure configureClosure = {
            // manipulate some property
            executed = true
        }
        TestArchiveTask testTask = bundle.createArchive(testArchiveType, configureClosure)
        checkCommonStuff(testTask, "${bundle.tasksBaseName}_${testArchiveType.defaultExtension}", testArchiveType.conventionMapping, bundle.tasksBaseName)
        assertTrue(testTask.executed)
    }

    private AbstractArchiveTask checkForDefaultValues(AbstractArchiveTask archiveTask, ArchiveType archiveType) {
        checkCommonStuff(archiveTask, "${bundle.tasksBaseName}_${archiveType.defaultExtension}", archiveType.conventionMapping, bundle.tasksBaseName)
    }

    private AbstractArchiveTask checkCommonStuff(AbstractArchiveTask archiveTask, String expectedArchiveTaskName,
                                                 Map conventionMapping, String expectedArchiveBaseName) {
        assert archiveTask.conventionMapping.is(conventionMapping)
        assertEquals(expectedArchiveTaskName, archiveTask.name)
        assertEquals(expectedArchiveBaseName, archiveTask.baseName)
        assertEquals([expectedArchiveTaskName] as Set, bundle.dependsOn)
        assertEquals(testChildrenDependsOn as Set, archiveTask.dependsOn)
        assert bundle.archiveNames.contains(archiveTask.name)
        archiveTask
    }

}
