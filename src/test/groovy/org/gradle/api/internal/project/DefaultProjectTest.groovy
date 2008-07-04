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

package org.gradle.api.internal.project

import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import java.text.FieldPosition
import org.apache.tools.ant.types.FileSet
import org.gradle.api.*
import org.gradle.api.internal.DefaultTask
import org.gradle.api.internal.dependencies.DefaultDependencyManager
import org.gradle.api.internal.dependencies.DefaultDependencyManagerFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginTest
import org.gradle.api.tasks.Directory
import org.gradle.api.tasks.util.BaseDirConverter
import org.gradle.util.HelperUtil
import org.gradle.util.TestTask
import org.gradle.api.plugins.Convention
import org.junit.Test
import org.junit.Before
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertSame
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertFalse
import org.jmock.Mockery
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.runner.RunWith

/**
 * @author Hans Dockter
 * todo: test for relativeFilePath
 */
@RunWith (org.jmock.integration.junit4.JMock.class)
class DefaultProjectTest {
    static final String TEST_PROJECT_NAME = 'testproject'

    static final String TEST_BUILD_FILE_NAME = 'build.gradle'

    static final String TEST_TASK_NAME = 'testtask'

    static final File TEST_ROOT = new File("root")

    DefaultProject project, child1, child2, childchild

    ProjectFactory projectFactory

    BuildScriptProcessor buildScriptProcessor

    ClassLoader buildScriptClassLoader

    DependencyManager dependencyManager

    PluginRegistry pluginRegistry
    ProjectRegistry projectRegistry

    File rootDir

    Script testScript

    Mockery context = new JUnit4GroovyMockery()


    @Before
    void setUp() {
        context.imposteriser = ClassImposteriser.INSTANCE
        testScript = new EmptyScript()
        buildScriptClassLoader = new URLClassLoader([] as URL[])
        rootDir = new File("/path/root")
        pluginRegistry = new PluginRegistry(new File('somepath'))
        projectRegistry = new ProjectRegistry()
        buildScriptProcessor = new BuildScriptProcessor()
        dependencyManager = new DefaultDependencyManager()
        projectFactory = new ProjectFactory(new DefaultDependencyManagerFactory(new File('root')), buildScriptProcessor,
                pluginRegistry, TEST_BUILD_FILE_NAME, projectRegistry)
        project = new DefaultProject('root', null, rootDir, null, TEST_BUILD_FILE_NAME, buildScriptClassLoader,
                projectFactory, dependencyManager, buildScriptProcessor, pluginRegistry, projectRegistry);
        child1 = project.addChildProject("child1")
        childchild = child1.addChildProject("childchild")
        child2 = project.addChildProject("child2")
        //        addScriptToProject(getListWithAllProjects())
    }

    private addScriptToProject(List projects) {
        projects.each {DefaultProject project ->
            project.script = testScript
        }
    }

    @Test void testProject() {
        assertSame(rootDir, project.rootDir)
        assertSame null, project.parent
        assertSame project, project.rootProject
        assertEquals(TEST_BUILD_FILE_NAME, project.buildFileName)
        assertSame project.buildScriptClassLoader, buildScriptClassLoader
        assertSame projectFactory, project.projectFactory
        assertSame buildScriptProcessor, project.buildScriptProcessor
        assertNotNull(project.ant)
        assertNull(project.configureByDag)
        assertNotNull(project.convention)
        assert project.dependencies.is(dependencyManager)
        assert dependencyManager.project.is(project)
        assert pluginRegistry.is(project.pluginRegistry)
        assert project.projectRegistry.getProject(project.path).is(project)
        assert projectRegistry.is(project.projectRegistry)
        assertEquals 'root', project.name
        assertEquals 'root', project.archivesBaseName
        assertEquals([:], project.pluginApplyRegistry)
        assertEquals DefaultProject.STATE_CREATED, project.state
        assertEquals DefaultProject.DEFAULT_BUILD_DIR_NAME, project.buildDirName

        assertSame project, child1.parent
        assertSame project, child1.rootProject
    }

    @Test void testEvaluate() {
        final String expectedProp = 'testprop'
        Task nestedTask
        Closure lateInitClosure = {
            nestedTask = project.createTask("$name:addon") {}
        }
        project.createTask(TEST_TASK_NAME, (DefaultProject.TASK_TYPE): TestTask, (DefaultProject.TASK_TYPE_LATE_INITIALIZER): [lateInitClosure])
        // We need a second task to check for cocurrent modification exception 
        project.createTask(TEST_TASK_NAME + "_2", (DefaultProject.TASK_TYPE): TestTask, (DefaultProject.TASK_TYPE_LATE_INITIALIZER): [lateInitClosure])

        BuildScriptProcessor buildScriptProcessorMocker = context.mock(BuildScriptProcessor)
        project.buildScriptProcessor = buildScriptProcessorMocker
        context.checking {
            one(buildScriptProcessorMocker).createScript(project); will(returnValue(testScript))
        }

        assertSame(project, project.evaluate())

        assertEquals(DefaultProject.STATE_INITIALIZED, project.state)
        assertTrue(project.tasks[TEST_TASK_NAME].lateInitialized)
        assertTrue(nestedTask.lateInitialized)
        assert project.buildScript.is(testScript)
    }

    @Test void testUsePluginWithString() {
        checkUsePlugin('someplugin')
    }

    @Test void testUsePluginWithClass() {
        checkUsePlugin(JavaPluginTest)
    }

    private void checkUsePlugin(def usePluginArgument) {
        Map expectedCustomValues = [:]
        Plugin mockPlugin = [:] as JavaPlugin

        PluginRegistry pluginRegistryMock = context.mock(PluginRegistry);
        project.pluginRegistry = pluginRegistryMock
        context.checking {
            one(pluginRegistryMock).getPlugin(usePluginArgument); will(returnValue(mockPlugin))
            one(pluginRegistryMock).apply(mockPlugin.class, project, pluginRegistryMock, expectedCustomValues)
        }

        project.usePlugin(usePluginArgument, expectedCustomValues)

        assert project.plugins[0].is(mockPlugin)
        assertEquals(1, project.plugins.size())
    }

    @Test (expected = InvalidUserDataException) void testUsePluginWithNonExistentPlugin() {
        String unknownPluginName = 'someplugin'
        PluginRegistry pluginRegistryMock = context.mock(PluginRegistry);
        project.pluginRegistry = pluginRegistryMock
        context.checking {
            one(pluginRegistryMock).getPlugin(unknownPluginName); will(returnValue(null))
        }
        project.usePlugin(unknownPluginName)
    }

    @Test void testEvaluationDependsOn() {
        boolean mockReader2Finished = false
        boolean mockReader1Called = false
        final BuildScriptProcessor mockReader1 = [createScript: {DefaultProject project ->
            project.evaluationDependsOn(child1.path)
            assertTrue(mockReader2Finished)
            mockReader1Called = true
            testScript
        }] as BuildScriptProcessor
        final BuildScriptProcessor mockReader2 = [
                createScript: {DefaultProject project ->
                    mockReader2Finished = true
                    testScript
                }] as BuildScriptProcessor
        project.buildScriptProcessor = mockReader1
        child1.buildScriptProcessor = mockReader2
        project.evaluate()
        assertTrue mockReader1Called
    }

    @Test (expected = InvalidUserDataException) void testEvaluationDependsOnWithNullArgument() {
        project.evaluationDependsOn(null)
    }

    @Test (expected = InvalidUserDataException) void testEvaluationDependsOnWithEmptyArgument() {
        project.evaluationDependsOn('')
    }

    @Test (expected = CircularReferenceException) void testEvaluationDependsOnWithCircularDependency() {
        final BuildScriptProcessor mockReader1 = [createScript: {DefaultProject project ->
            project.evaluationDependsOn(child1.path)
            testScript
        }] as BuildScriptProcessor
        final BuildScriptProcessor mockReader2 = [createScript: {DefaultProject project ->
            project.evaluationDependsOn(project.path)
            testScript
        }] as BuildScriptProcessor
        project.buildScriptProcessor = mockReader1
        child1.buildScriptProcessor = mockReader2
        project.evaluate()
    }

    @Test void testDependsOnWithNoEvaluation() {
        boolean mockReaderCalled = false
        final BuildScriptProcessor mockReader = [createScript: {DefaultProject project ->
            mockReaderCalled = true
            testScript
        }] as BuildScriptProcessor
        child1.buildScriptProcessor = mockReader
        project.dependsOn(child1.name, false)
        assertFalse mockReaderCalled
        assertEquals([child1] as Set, project.dependsOnProjects)
        project.dependsOn(child2.path, false)
        assertEquals([child1, child2] as Set, project.dependsOnProjects)
    }

    @Test void testDependsOn() {
        boolean mockReaderCalled = false
        final BuildScriptProcessor mockReader = [createScript: {DefaultProject project ->
            mockReaderCalled = true
            testScript
        }] as BuildScriptProcessor
        child1.buildScriptProcessor = mockReader
        project.dependsOn(child1.name)
        assertTrue mockReaderCalled
        assertEquals([child1] as Set, project.dependsOnProjects)

    }

    @Test void testChildrenDependsOnMe() {
        project.childrenDependOnMe()
        assertTrue(child1.dependsOnProjects.contains(project))
        assertTrue(child2.dependsOnProjects.contains(project))
        assertEquals(1, child1.dependsOnProjects.size())
        assertEquals(1, child2.dependsOnProjects.size())
    }

    @Test void testDependsOnChildren() {
        BuildScriptProcessor buildScriptProcessorMocker = context.mock(BuildScriptProcessor)
        child1.buildScriptProcessor = buildScriptProcessorMocker
        context.checking {
            never(buildScriptProcessorMocker).createScript(child1)
        }

        project.dependsOnChildren()
        context.assertIsSatisfied()
        assertTrue(project.dependsOnProjects.contains(child1))
        assertTrue(project.dependsOnProjects.contains(child2))
        assertEquals(2, project.dependsOnProjects.size())
    }

    @Test void testDependsOnChildrenIncludingEvaluate() {
        BuildScriptProcessor buildScriptProcessorMocker = context.mock(BuildScriptProcessor)
        child1.buildScriptProcessor = buildScriptProcessorMocker
        child2.buildScriptProcessor = buildScriptProcessorMocker
        context.checking {
            one(buildScriptProcessorMocker).createScript(child1); will(returnValue(testScript))
            one(buildScriptProcessorMocker).createScript(child2); will(returnValue(testScript))
        }
        project.dependsOnChildren(true)
        assertTrue(project.dependsOnProjects.contains(child1))
        assertTrue(project.dependsOnProjects.contains(child2))
        assertEquals(2, project.dependsOnProjects.size())
    }

    @Test (expected = InvalidUserDataException) void testDependsOnWithNullPath() {
        project.dependsOn(null)
    }

    @Test (expected = InvalidUserDataException) void testDependsOnWithEmptyPath() {
        project.dependsOn('')
    }

    @Test (expected = UnknownProjectException) void testDependsOnWithUnknownParentPath() {
        project.dependsOn(child1.path + 'XXX')
    }

    @Test (expected = UnknownProjectException) void testDependsOnWithUnknownProjectPath() {
        project.dependsOn(child1.name + 'XXX')
    }

    @Test void testAddAndGetChildProject() {
        DefaultProject dummyChild1 = HelperUtil.createChildProject(project, '/child2/childchild1')
        DefaultProject dummyChild2 = HelperUtil.createChildProject(project, '/child2/childchild2')


        ProjectFactory mockProjectFactory = context.mock(ProjectFactory)
        child2.projectFactory = mockProjectFactory
        context.checking {
            one(mockProjectFactory).createProject(dummyChild1.name, child2, rootDir, project, buildScriptClassLoader); will(returnValue(dummyChild1))
            one(mockProjectFactory).createProject(dummyChild2.name, child2, rootDir, project, buildScriptClassLoader); will(returnValue(dummyChild2))
        }

        child2.addChildProject(dummyChild1.name)
        child2.addChildProject(dummyChild2.name)
        assertEquals(2, project.getChildProjects().size())
        assertEquals([(dummyChild1.name): dummyChild1, (dummyChild2.name): dummyChild2] as HashMap, child2.getChildProjects())
    }

    @Test void testCreateTask() {
        DefaultTask task = project.createTask(TEST_TASK_NAME)
        assertEquals(0, task.actions.size())
        checkTask project, task
    }

    @Test void testCreateTaskWithActions() {
        TaskAction testaction = {} as TaskAction
        DefaultTask task = project.createTask(TEST_TASK_NAME, testaction)
        assertEquals(TEST_TASK_NAME, task.name)
        assertEquals([testaction], task.actions)
        checkTask project, task
    }

    @Test void testCreateTaskWithDependencies() {
        List testDependsOn = ['/path1']
        DefaultTask task = project.createTask([dependsOn: testDependsOn], TEST_TASK_NAME)
        assertEquals(TEST_TASK_NAME, task.name)
        assertEquals(testDependsOn as Set, task.dependsOn)
        assert !task.actions
        checkTask project, task
    }

    @Test void testCreateTaskWithSingleDependency() {
        String testDependsOn = '/path1'
        DefaultTask task = project.createTask([dependsOn: testDependsOn], TEST_TASK_NAME)
        assertEquals(TEST_TASK_NAME, task.name)
        assertEquals([testDependsOn] as Set, task.dependsOn)
        assert !task.actions
        checkTask project, task
    }

    @Test void testCreateTaskWithActionAndDependencies() {
        List testDependsOn = ['/path1', 'path2', 'path2/path3']
        TaskAction testaction = {} as TaskAction
        DefaultTask task = project.createTask([dependsOn: testDependsOn], TEST_TASK_NAME, testaction)
        assertEquals(TEST_TASK_NAME, task.name)
        Set testSet = HelperUtil.pureStringTransform(new HashSet(['/path1', "path2", "path2/path3"]))
        assertEquals('dependencies', testSet, task.dependsOn)
        assertEquals('actions', [testaction], task.actions)
        checkTask project, task
    }

    @Test (expected = InvalidUserDataException) void testCreateDefaultTaskWithSameNameAsExistingTask() {
        Task task = project.createTask(TEST_TASK_NAME)
        project.createTask(TEST_TASK_NAME)
    }

    @Test void testCreateDefaultTaskWithSameNameAsExistingTaskAndOverwriteTrue() {
        Task task = project.createTask(TEST_TASK_NAME)
        Task newTask = project.createTask([overwrite: true], TEST_TASK_NAME)
        assert !newTask.is(task)
    }

    @Test void testCreateTaskWithNonDefaultType() {
        TestTask task = project.createTask(TEST_TASK_NAME, (DefaultProject.TASK_TYPE): TestTask)
        assertEquals(TestTask, project.tasks[TEST_TASK_NAME].class)
        checkTask project, task
    }

    private void checkTask(DefaultProject project, Task task) {
        assertEquals(TEST_TASK_NAME, task.name)
        assertSame(project, task.project)
        assertSame(task, project.task(TEST_TASK_NAME))
    }

    @Test void testTask() {
        DefaultTask task = project.createTask(TEST_TASK_NAME)
        assert project.task(TEST_TASK_NAME).is(task)
        assert project."$TEST_TASK_NAME".is(task)
    }

    @Test void testTaskWithConfigureClosure() {
        Closure testConfigureClosure = {}
        Task mockTask = context.mock(Task)
        project.tasks[TEST_TASK_NAME] = mockTask
        context.checking {
            one(mockTask).configure(testConfigureClosure); will(returnValue(mockTask))
        }

        assert mockTask.is(project.task(TEST_TASK_NAME, testConfigureClosure))
    }


    @Test (expected = InvalidUserDataException) void testTaskWithNonExistingTask() {
        project.task(TEST_TASK_NAME)
    }

    @Test (expected = MissingPropertyException) void testPropertyShortCutForTaskCallWithNonExistingTask() {
        project.unknownTask
    }

    @Test (expected = MissingMethodException) void testMethodShortCutForTaskCallWithNonExistingTask() {
        project.unknownTask([dependsOn: '/task2'])
    }

    private Set getListWithAllProjects() {
        [project, child1, child2, childchild]
    }

    private Set getListWithAllChildProjects() {
        [child1, child2, childchild]

    }

    //
    //    void testTaskWithNonDefaultTypeAndTypeInitializer() {
    //        Closure initClosure = {}
    //        project.task(TEST_TASK_NAME, (DefaultProject.TASK_TYPE): TestTask, (DefaultProject.TASK_TYPE_INITIALIZER): initClosure)
    //        assertNotNull(project.tasks[TEST_TASK_NAME])
    //        assertEquals(TestTask, project.tasks[TEST_TASK_NAME].class)
    //        assertSame initClosure, project.tasks[TEST_TASK_NAME].initalizeClosure
    //    }


    @Test void testDependenciesWithConfigureClosure() {
        Closure testConfigureClosure = {
            getBuildResolverDir()
        }

        DependencyManager mockDependencyManager = context.mock(DependencyManager)
        project.dependencies = mockDependencyManager
        context.checking {
            one(mockDependencyManager).getBuildResolverDir(); will(returnValue(null))
        }

        assert mockDependencyManager.is(project.dependencies(testConfigureClosure))
    }


    @Test void testPath() {
        assertEquals(Project.PATH_SEPARATOR + "child1", child1.path)
        assertEquals(Project.PATH_SEPARATOR, project.path)
    }

    @Test void testGetProject() {
        assertSame(project, project.project(Project.PATH_SEPARATOR))
        assertSame(child1, project.project(Project.PATH_SEPARATOR + "child1"))
        assertSame(child1, project.project("child1"))
        assertSame(childchild, child1.project('childchild'))
        assertSame(child1, childchild.project(Project.PATH_SEPARATOR + "child1"))
    }

    @Test (expected = UnknownProjectException) void testGetProjectWithUnknownAbsolutePath() {
        project.project(Project.PATH_SEPARATOR + "unknownchild")
    }

    @Test (expected = UnknownProjectException) void testGetProjectWithUnknownRelativePath() {
        project.project("unknownChild")
    }

    @Test (expected = InvalidUserDataException) void testGetProjectWithEmptyPath() {
        project.project("")
    }

    @Test (expected = InvalidUserDataException) void testGetProjectWithNullPath() {
        project.project(null)
    }

    @Test void testGetProjectWithClosure() {
        String newPropValue = 'someValue'
        assert child1.is(project.project("child1") {
            newProp = newPropValue
        })
        assertEquals(child1.newProp, newPropValue)
    }

    @Test void testGetAllTasks() {
        List tasksClean = project.allprojects*.createTask('clean')
        List tasksCompile = project.allprojects*.createTask('compile')
        SortedMap expectedMap = new TreeMap()
        (tasksClean + tasksCompile).each {Task task ->
            if (!expectedMap[task.project]) {
                expectedMap[task.project] = new TreeSet()
            }
            expectedMap[task.project].add(task)
        }
        assertEquals(expectedMap, project.getAllTasks(true))
        assertEquals(expectedMap.subMap([project]), project.getAllTasks(false))
    }

    @Test void testGetTasksByName() {
        Set tasksClean = project.allprojects*.createTask('clean')
        project.allprojects*.createTask('compile')
        Set expectedMap = new HashSet()
        assertEquals(tasksClean, project.getTasksByName('clean', true))
        assertEquals([project.tasks['clean']] as Set, project.getTasksByName('clean', false))
    }

    @Test void testGetTasksByNameWithSingularTask() {
        DefaultTask child1Task = child1.createTask('child1Task')
        assertEquals([child1Task] as Set, project.getTasksByName(child1Task.name, true))
        assertEquals(0, project.getTasksByName(child1Task.name, false).size())
    }

    @Test (expected = InvalidUserDataException) void testGetTasksWithEmptyName() {
        project.getTasksByName('', true)
    }

    @Test (expected = InvalidUserDataException) void testGetTasksWithNullName() {
        project.getTasksByName(null, true)
    }

    @Test void testGetTasksWithUnknownName() {
        project.allprojects*.createTask('clean')
        assertEquals(0, project.getTasksByName('cleanXXX', true).size())
        assertEquals(0, project.getTasksByName('cleanXXX', false).size())
    }

    @Test void testMethodMissing() {
        DefaultProject dummyParentProject = [scriptMethod: {Closure closure -> 'parent'}] as DefaultProject
        project.parent = dummyParentProject
        boolean closureCalled = false
        Closure testConfigureClosure = {closureCalled = true}
        assertEquals('parent', project.scriptMethod(testConfigureClosure))
        project.createTask('scriptMethod')
        project.scriptMethod(testConfigureClosure)
        assert closureCalled
        project.convention.plugins.test = new TestConvention()
        assertEquals(TestConvention.METHOD_RESULT, project.scriptMethod(testConfigureClosure))
        Script projectScript = createScriptForMethodMissingTest('projectScript')
        project.buildScript = projectScript
        assertEquals('projectScript', project.scriptMethod(testConfigureClosure))
    }

    private Script createScriptForMethodMissingTest(String returnValue) {
        String code = """
def scriptMethod(Closure closure) {
    "$returnValue"
}
"""
        new GroovyShell().parse(code)
    }

    @Test void testSetPropertyAndPropertyMissingWithProjectProperty() {
        String propertyName = 'propName'
        String expectedValue = 'somevalue'

        project."$propertyName" = expectedValue
        assertEquals(expectedValue, project."$propertyName")
        assertEquals(expectedValue, child1."$propertyName")
    }

    @Test void testPropertyMissingWithExistingConventionProperty() {
        String propertyName = 'conv'
        String expectedValue = 'somevalue'
        project.convention.plugins.test = new TestConvention()
        project.convention.conv = expectedValue
        assertEquals(expectedValue, project."$propertyName")
        assertEquals(expectedValue, project.convention."$propertyName")
        assertEquals(expectedValue, child1."$propertyName")
    }

    @Test void testSetPropertyAndPropertyMissingWithConventionProperty() {
        String propertyName = 'conv'
        String expectedValue = 'somevalue'
        project.convention.plugins.test = new TestConvention()
        project."$propertyName" = expectedValue
        assertEquals(expectedValue, project."$propertyName")
        assertEquals(expectedValue, project.convention."$propertyName")
        assertEquals(expectedValue, child1."$propertyName")
    }

    @Test void testSetPropertyAndPropertyMissingWithProjectAndConventionProperty() {
        String propertyName = 'name'
        String expectedValue = 'somename'

        project.name = expectedValue
        project.convention.plugins.test = new TestConvention()
        project.convention.name = 'someothername'
        project."$propertyName" = expectedValue
        assertEquals(expectedValue, project."$propertyName")
        assertEquals('someothername', project.convention."$propertyName")
    }

    @Test void testPropertyMissingWithNullProperty() {
        project.nullProp = null
        assertNull(project.nullProp)
        assert project.hasProperty('nullProp')
    }

    @Test (expected = MissingPropertyException)
    void testPropertyMissingWithUnknownProperty() {
        project.unknownProperty
    }

    @Test void testHasProperty() {
        assertTrue(project.hasProperty('name'))
        String propertyName = 'beginIndex'
        assertFalse(project.hasProperty(propertyName))
        assertFalse(child1.hasProperty(propertyName))

        project.convention.plugins.test = new FieldPosition(0)
        project.convention."$propertyName" = 5
        assertTrue(project.hasProperty(propertyName))
        assertTrue(child1.hasProperty(propertyName))
        project.convention = new Convention(project)
        project."$propertyName" = 4
        assertTrue(project.hasProperty(propertyName))
        assertTrue(child1.hasProperty(propertyName))
    }

    @Test void testAdditionalProperty() {
        String expectedPropertyName = 'somename'
        String expectedPropertyValue = 'somevalue'
        project.additionalProperties[expectedPropertyName] = expectedPropertyValue
        assertEquals(project."$expectedPropertyName", expectedPropertyValue)
    }

    @Test void testGetProjectProperty() {
        assert project.is(project.getProject())
    }

    @Test void testAllprojectsField() {
        assertEquals(getListWithAllProjects(), project.allprojects)
    }

    @Test void testChildren() {
        assertEquals(getListWithAllChildProjects(), project.subprojects)
    }

    @Test void testProjectDir() {
        assertEquals(new File("${rootDir.path}/${child1.name}"), child1.projectDir)
    }

    @Test void testBuildDir() {
        assertEquals(new File(child1.projectDir, "${Project.DEFAULT_BUILD_DIR_NAME}"), child1.buildDir)
    }

    @Test void testFile() {
        String expectedPath = 'somepath'
        PathValidation expectedValidation = PathValidation.FILE
        boolean converterCalled = false
        child1.baseDirConverter = [baseDir: {String path, File baseDir, PathValidation pathValidation ->
            converterCalled = true
            assertEquals(expectedPath, path)
            assertEquals(new File("${rootDir.path}/${child1.name}"), baseDir)
            assertEquals(expectedValidation, pathValidation)
        }] as BaseDirConverter
        child1.file(expectedPath, PathValidation.FILE)
        assertTrue(converterCalled)

        converterCalled = false
        expectedValidation = PathValidation.NONE
        child1.file(expectedPath)
        assertTrue(converterCalled)
    }

    @Test void testDir() {
        Task task = project.dir('dir1/dir2/dir3')
        assert task instanceof Directory
        assertEquals('dir1/dir2/dir3', task.name)
        Task dir1Task = project.task('dir1')
        assert dir1Task instanceof Directory
        assert project.task('dir1/dir2') instanceof Directory
        project.dir('dir1/dir4')
        assert project.task('dir1').is(dir1Task)
        assert project.task('dir1/dir4') instanceof Directory
    }

    @Test void testLazyInitOfAnt() {
        assertNotNull(project.ant)
        assert project.ant.is(project.ant)
    }

    @Test void testAnt() {
        Closure configureClosure = {fileset(dir: 'dir')}
        project.ant(configureClosure)
        assertEquals(Closure.OWNER_FIRST, configureClosure.@resolveStrategy)
        assertTrue(project.ant.collectorTarget.children[0].realThing instanceof FileSet)
    }

    @Test void testSubprojects() {
        checkConfigureProject('subprojects', listWithAllChildProjects)
    }

    @Test void testAllprojects() {
        checkConfigureProject('allprojects', listWithAllProjects)
    }

    @Test void testConfigureProjects() {
        checkConfigureProject('configureProjects', [project, child1] as Set)
    }

    private void checkConfigureProject(String configureMethod, Set projectsToCheck) {
        String propValue = 'someValue'
        if (configureMethod == 'configureProjects') {
            project."$configureMethod" projectsToCheck as List,
                    {
                        testSubProp = propValue
                    }
        } else {
            project."$configureMethod"
            {
                testSubProp = propValue
            }
        }

        projectsToCheck.each {
            assertEquals(propValue, it.testSubProp)
        }
    }
}

class TestConvention {
    final static String METHOD_RESULT = 'methodResult'
    String name
    String conv

    def scriptMethod(Closure cl) {
        METHOD_RESULT
    }
}

