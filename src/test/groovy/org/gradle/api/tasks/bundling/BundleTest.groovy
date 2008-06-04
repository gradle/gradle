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
    static final File TEST_DESTINATION_DIR = new File('testdestdir')
    
    Bundle bundle

    ArchiveType testArchiveType

    Map testConventionMapping

    String testDefaultSuffix

    String testTasksBaseName

    List testChildrenDependsOn

    List testBundleDependsOn

    String expectedArchiveName

    String customTaskName

    String expectedDefaultArchiveName

    Map testArgs

    Closure testClosure

    Task getTask() {bundle}

    void setUp() {
        super.setUp()
        testArgs = [baseName: 'testBasename', classifier: 'testClassifier']
        testClosure = {
            destinationDir = TEST_DESTINATION_DIR
        }
        testChildrenDependsOn = ['othertaskpath', 'othertaskpath2']
        testBundleDependsOn = ['othertaskpath10', 'othertaskpath11']
        bundle = new Bundle(project, AbstractTaskTest.TEST_TASK_NAME)
        bundle.childrenDependOn = testChildrenDependsOn
        bundle.dependsOn = testBundleDependsOn
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
        (Jar) checkForDefaultValues(bundle.jar(testClosure), bundle.defaultArchiveTypes.jar)
    }

    void testJarWithArgs() {
        (Jar) checkForDefaultValues(bundle.jar(testArgs, testClosure), bundle.defaultArchiveTypes.jar, testArgs)
    }

    void testZipWithDefaultValues() {
        (Zip) checkForDefaultValues(bundle.zip(testClosure), bundle.defaultArchiveTypes.zip)
    }

    void testZipWithArgs() {
        (Zip) checkForDefaultValues(bundle.zip(testArgs, testClosure), bundle.defaultArchiveTypes.zip, testArgs)
    }

    void testWarWithDefaultValues() {
        (War) checkForDefaultValues(bundle.war(testClosure), bundle.defaultArchiveTypes.war)
    }

    void testWarWithArgs() {
        (War) checkForDefaultValues(bundle.war(testArgs, testClosure), bundle.defaultArchiveTypes.war, testArgs)
    }

    void testTarWithDefaultValues() {
        (Tar) checkForDefaultValues(bundle.tar(testClosure), bundle.defaultArchiveTypes.tar)
    }

    void testTarWithArgs() {
        (Tar) checkForDefaultValues(bundle.tar(testArgs, testClosure), bundle.defaultArchiveTypes.tar, testArgs)
    }

    void testTarGzWithDefaultValues() {
        (Tar) checkForDefaultValues(bundle.tarGz(testClosure), bundle.defaultArchiveTypes['tar.gz'])
    }

    void testTarGzWithArgs() {
        (Tar) checkForDefaultValues(bundle.tarGz(testArgs, testClosure), bundle.defaultArchiveTypes['tar.gz'], testArgs)
    }

    void testTarBzip2WithDefaultValues() {
        (Tar) checkForDefaultValues(bundle.tarBzip2(testClosure), bundle.defaultArchiveTypes['tar.bzip2'])
    }

    void testTarBzip2WithArgs() {
        (Tar) checkForDefaultValues(bundle.tarBzip2(testArgs, testClosure), bundle.defaultArchiveTypes['tar.bzip2'], testArgs)
    }

    void testCreateArchiveWithDefaultValues() {
        (TestArchiveTask) checkForDefaultValues(bundle.createArchive(testArchiveType, testClosure), testArchiveType)
    }

    void testCreateArchiveWithCustomName() {
        TestArchiveTask testTask = bundle.createArchive(testArchiveType, testArgs, testClosure)
        checkForDefaultValues(testTask, testArchiveType, testArgs)
    }

    void testChildrenDependsOn() {
        AbstractArchiveTask task1 = bundle.zip(baseName: 'zip1')
        AbstractArchiveTask task2 = bundle.zip(baseName: 'zip2')
        assertEquals(testChildrenDependsOn as Set, task1.dependsOn)
        assertEquals(testChildrenDependsOn as Set, task2.dependsOn)
    }

    private AbstractArchiveTask checkForDefaultValues(AbstractArchiveTask archiveTask, ArchiveType archiveType, Map args = [:]) {
        String baseName = args.baseName ?: bundle.tasksBaseName
        String classifier = args.classifier ? '_' + args.classifier  : ''
        checkCommonStuff(archiveTask, "${baseName}${classifier}_${archiveType.defaultExtension}",
                archiveType.conventionMapping, baseName, classifier)
    }

    private AbstractArchiveTask checkCommonStuff(AbstractArchiveTask archiveTask, String expectedArchiveTaskName,
                                                 Map conventionMapping, String expectedArchiveBaseName, String expectedArchiveClassifier) {
        assertEquals(TEST_DESTINATION_DIR, archiveTask.destinationDir)
        assert archiveTask.conventionMapping.is(conventionMapping)
        assertEquals(expectedArchiveTaskName, archiveTask.name)
        assertEquals(expectedArchiveBaseName, archiveTask.baseName)
        assertEquals(expectedArchiveClassifier, archiveTask.classifier)
        assertEquals((testBundleDependsOn + [expectedArchiveTaskName]) as Set, bundle.dependsOn)
        assertEquals(testChildrenDependsOn as Set, archiveTask.dependsOn)
        assert bundle.archiveNames.contains(archiveTask.name)
        archiveTask
    }

}
