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

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.project.ITaskFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.DefaultTaskContainer
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.AbstractConventionTaskTest
import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.api.tasks.ConventionValue
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.tasks.bundling.*
import static org.junit.Assert.*
import org.gradle.api.tasks.TaskContainer


/**
 * @author Hans Dockter
 */
@RunWith (JMock)
class BundleTest extends AbstractConventionTaskTest {

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

    List testDefaultConfigurations

    List testCustomConfigurations

    Map testArgs

    Closure testClosure

    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    Task taskMock;

    ITaskFactory taskFactoryMock;

    File testDefaultDestinationDir

    AbstractTask getTask() {bundle}

    @Before public void setUp() {
        super.setUp()
        context.setImposteriser(ClassImposteriser.INSTANCE)
        testDefaultDestinationDir = 'someDestDir' as File
        taskMock = context.mock(Task)
        taskFactoryMock = context.mock(ITaskFactory)
        getProject().setTaskContainer(new DefaultTaskContainer(getProject(), taskFactoryMock))
        testCustomConfigurations = ['customConf1', 'customConf2']
        testArgs = [baseName: 'testBasename', appendix: 'testAppendix', classifier: 'testClassifier',
                confs: testCustomConfigurations]
        testClosure = {
            enabled = false
        }
        testChildrenDependsOn = ['othertaskpath', 'othertaskpath2']
        testBundleDependsOn = ['othertaskpath10', "othertaskpath11"]
        testDefaultConfigurations = ['conf1', 'conf2']
        bundle = new Bundle(project, AbstractTaskTest.TEST_TASK_NAME)
        bundle.childrenDependOn = testChildrenDependsOn
        bundle.dependsOn = testBundleDependsOn
        bundle.defaultArchiveTypes = JavaPluginConvention.DEFAULT_ARCHIVE_TYPES
        bundle.defaultConfigurations = testDefaultConfigurations
        bundle.defaultDestinationDir = testDefaultDestinationDir
        customTaskName = 'customtaskname'
        expectedArchiveName = "${testTasksBaseName}_${testDefaultSuffix}"
        expectedDefaultArchiveName = "${testTasksBaseName}_${testDefaultSuffix}"
        testArchiveType = new ArchiveType('suf', [baseName: {conv, task -> getProject().getArchivesBaseName()} as ConventionValue], TestArchiveTask)
    }

    @Test public void testBundle() {
        bundle = new Bundle(project, AbstractTaskTest.TEST_TASK_NAME)
        assertEquals([] as Set, bundle.childrenDependOn)
    }

    @Test public void testJarWithDefaultValues() {
        prepateProjectMock(bundle.defaultArchiveTypes.jar, [:])
        (Jar) checkForDefaultValues(bundle.jar(testClosure), bundle.defaultArchiveTypes.jar)
    }

    @Test public void testJarWithArgs() {
        prepateProjectMock(bundle.defaultArchiveTypes.jar, testArgs)
        (Jar) checkForDefaultValues(bundle.jar(testArgs, testClosure), bundle.defaultArchiveTypes.jar, testArgs)
    }

    @Test public void testZipWithDefaultValues() {
        prepateProjectMock(bundle.defaultArchiveTypes.zip, [:])
        (Zip) checkForDefaultValues(bundle.zip(testClosure), bundle.defaultArchiveTypes.zip)
    }

    @Test public void testZipWithArgs() {
        prepateProjectMock(bundle.defaultArchiveTypes.zip, testArgs)
        (Zip) checkForDefaultValues(bundle.zip(testArgs, testClosure), bundle.defaultArchiveTypes.zip, testArgs)
    }

    @Test public void testWarWithDefaultValues() {
        prepateProjectMock(bundle.defaultArchiveTypes.war, [:])
        (War) checkForDefaultValues(bundle.war(testClosure), bundle.defaultArchiveTypes.war)
    }

    @Test public void testWarWithArgs() {
        prepateProjectMock(bundle.defaultArchiveTypes.war, testArgs)
        (War) checkForDefaultValues(bundle.war(testArgs, testClosure), bundle.defaultArchiveTypes.war, testArgs)
    }

    @Test public void testTarWithDefaultValues() {
        prepateProjectMock(bundle.defaultArchiveTypes.tar, [:])
        (Tar) checkForDefaultValues(bundle.tar(testClosure), bundle.defaultArchiveTypes.tar)
    }

    @Test public void testTarWithArgs() {
        prepateProjectMock(bundle.defaultArchiveTypes.tar, testArgs)
        (Tar) checkForDefaultValues(bundle.tar(testArgs, testClosure), bundle.defaultArchiveTypes.tar, testArgs)
    }

    @Test public void testTarGzWithDefaultValues() {
        prepateProjectMock(bundle.defaultArchiveTypes['tar.gz'], [:])
        (Tar) checkForDefaultValues(bundle.tarGz(testClosure), bundle.defaultArchiveTypes['tar.gz'])
    }

    @Test public void testTarGzWithArgs() {
        prepateProjectMock(bundle.defaultArchiveTypes['tar.gz'], testArgs)
        (Tar) checkForDefaultValues(bundle.tarGz(testArgs, testClosure), bundle.defaultArchiveTypes['tar.gz'], testArgs)
    }

    @Test public void testTarBzip2WithDefaultValues() {
        prepateProjectMock(bundle.defaultArchiveTypes['tar.bzip2'], [:])
        (Tar) checkForDefaultValues(bundle.tarBzip2(testClosure), bundle.defaultArchiveTypes['tar.bzip2'])
    }

    @Test public void testTarBzip2WithArgs() {
        prepateProjectMock(bundle.defaultArchiveTypes['tar.bzip2'], testArgs)
        (Tar) checkForDefaultValues(bundle.tarBzip2(testArgs, testClosure), bundle.defaultArchiveTypes['tar.bzip2'], testArgs)
    }

    @Test public void testCreateArchiveWithDefaultValues() {
        prepateProjectMock(testArchiveType, [:])
        (TestArchiveTask) checkForDefaultValues(bundle.createArchive(testArchiveType, testClosure), testArchiveType)
    }

    @Test public void testCreateArchiveWithArgs() {
        prepateProjectMock(testArchiveType, testArgs)
        TestArchiveTask testTask = bundle.createArchive(testArchiveType, testArgs, testClosure)
        checkForDefaultValues(testTask, testArchiveType, testArgs)
    }

    @Test public void testChildrenDependsOn() {
        preparForDependsOnTest();
        AbstractArchiveTask task1 = bundle.zip(baseName: 'zip1')
        AbstractArchiveTask task2 = bundle.zip(baseName: 'zip2')
        assertEquals(testChildrenDependsOn as Set, task1.dependsOn)
        assertEquals(testChildrenDependsOn as Set, task2.dependsOn)
    }

    @Test public void testEmptyChildrenDependsOn() {
        preparForDependsOnTest()
        bundle.childrenDependOn = []
        AbstractArchiveTask task1 = bundle.zip(baseName: 'zip1')
        AbstractArchiveTask task2 = bundle.zip(baseName: 'zip2')
        assertEquals(testBundleDependsOn as Set, task1.dependsOn)
        assertEquals(testBundleDependsOn as Set, task2.dependsOn)
    }

    private void preparForDependsOnTest(Map args = [:]) {
        Project projectMock = context.mock(ProjectInternal)
        TaskContainer taskContainer = context.mock(TaskContainer)
        bundle.setProject(projectMock)
        context.checking {
            allowing(projectMock).getArchivesBaseName(); will(returnValue(getProject().getArchivesBaseName()))
            allowing(projectMock).getArchivesTaskBaseName(); will(returnValue('archive'))
            allowing(projectMock).getTasks(); will(returnValue(taskContainer))
            one(taskContainer).add("zip1_zip", Zip)
            will(returnValue(Zip.newInstance(getProject(), "zip1_zip")))
            one(taskContainer).add("zip2_zip", Zip)
            will(returnValue(Zip.newInstance(getProject(), "zip2_zip")))
        }
    }

    private void prepateProjectMock(ArchiveType archiveType, Map args = [:]) {
        String baseName = args.baseName ?: getProject().archivesTaskBaseName
        String taskName = baseName + (args.appendix ? "_" + args.appendix : "")
        String classifier = args.classifier ? '_' + args.classifier : ''
        taskName = "${taskName}${classifier}_${archiveType.defaultExtension}"
        Project projectMock = context.mock(ProjectInternal)
        TaskContainer taskContainer = context.mock(TaskContainer)
        bundle.setProject(projectMock)
        context.checking {
            allowing(projectMock).getArchivesBaseName(); will(returnValue(getProject().getArchivesBaseName()))
            allowing(projectMock).getArchivesTaskBaseName(); will(returnValue(Project.DEFAULT_ARCHIVES_TASK_BASE_NAME))
            allowing(projectMock).getTasks(); will(returnValue(taskContainer))
            one(taskContainer).add(taskName, archiveType.getTaskClass())
            will(returnValue(createTask(archiveType.getTaskClass(), getProject(), taskName)))
        }
    }

    private AbstractArchiveTask checkForDefaultValues(AbstractArchiveTask archiveTask, ArchiveType archiveType, Map args = [:]) {
        String baseName = args.baseName ?: getProject().archivesTaskBaseName
        String taskName = baseName + (args.appendix ? "_" + args.appendix : "")
        String archiveBaseName = (args.baseName ?: getProject().archivesBaseName);
        String archiveAppendix = args.appendix
        String classifier = args.classifier ? '_' + args.classifier : ''
        checkCommonStuff(archiveTask, "${taskName}${classifier}_${archiveType.defaultExtension}",
                archiveType.conventionMapping, archiveBaseName, archiveAppendix, classifier ? classifier.substring(1) : '')
    }

    private AbstractArchiveTask checkCommonStuff(AbstractArchiveTask archiveTask, String expectedArchiveTaskName,
                                                 Map conventionMapping, String expectedArchiveBaseName, String expectedArchiveAppendix, String expectedArchiveClassifier) {
        assertEquals(false, archiveTask.enabled)
        assertEquals(conventionMapping, archiveTask.conventionMapping)
        assertEquals(expectedArchiveBaseName, archiveTask.baseName)
        assertEquals(expectedArchiveAppendix, archiveTask.appendix)
        assertEquals(expectedArchiveClassifier, archiveTask.classifier)
        assertEquals((testBundleDependsOn + [expectedArchiveTaskName]) as Set, bundle.dependsOn)
        assertEquals(testChildrenDependsOn as Set, archiveTask.dependsOn)
        assertEquals(testDefaultDestinationDir, archiveTask.getDestinationDir())
        assert bundle.archiveTasks.contains(archiveTask)
        archiveTask
    }

    @Test
    void setDefaultDestinationDirWithString() {
        bundle.setDefaultDestinationDir(testDefaultDestinationDir.absolutePath)
        assertEquals(testDefaultDestinationDir.absolutePath, bundle.getDefaultDestinationDir().absolutePath)
    }
}
