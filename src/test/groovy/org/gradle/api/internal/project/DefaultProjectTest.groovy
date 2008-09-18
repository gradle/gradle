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

import java.text.FieldPosition
import org.apache.tools.ant.types.FileSet
import org.gradle.api.*
import org.gradle.api.internal.DefaultTask
import org.gradle.api.internal.dependencies.DependencyManagerFactory
import org.gradle.api.plugins.Convention
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginTest
import org.gradle.api.tasks.Directory
import org.gradle.api.tasks.util.BaseDirConverter
import org.gradle.groovy.scripts.EmptyScript
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.WrapUtil
import static org.hamcrest.Matchers.*
import org.jmock.Mockery
import org.jmock.lib.legacy.ClassImposteriser
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test
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

    Task testTask;

    DefaultProject project, child1, child2, childchild

    BuildScriptProcessor buildScriptProcessor

    ClassLoader buildScriptClassLoader

    PluginRegistry pluginRegistry
    IProjectRegistry projectRegistry

    File rootDir

    Script testScript

    ScriptSource script;

    ITaskFactory taskFactoryMock;

    DependencyManagerFactory dependencyManagerFactoryMock;
    DependencyManager dependencyManagerMock;

    JUnit4GroovyMockery context = new JUnit4GroovyMockery()


    @Before
    void setUp() {
        context.imposteriser = ClassImposteriser.INSTANCE
        taskFactoryMock = context.mock(ITaskFactory.class);
        dependencyManagerMock = context.mock(DependencyManager)
        dependencyManagerFactoryMock = [createDependencyManager: {Project project -> dependencyManagerMock}] as DependencyManagerFactory
        script = context.mock(ScriptSource.class)

        testScript = new EmptyScript()
        buildScriptClassLoader = new URLClassLoader([] as URL[])
        rootDir = new File("/path/root").absoluteFile
        pluginRegistry = new PluginRegistry(new File('somepath'))
        projectRegistry = new DefaultProjectRegistry()
        buildScriptProcessor = new BuildScriptProcessor()
        ProjectFactory factory = new ProjectFactory(taskFactoryMock, dependencyManagerFactoryMock, buildScriptProcessor,
                pluginRegistry, TEST_BUILD_FILE_NAME, projectRegistry, null)
        project = new DefaultProject('root', null, rootDir, rootDir, TEST_BUILD_FILE_NAME, script, buildScriptClassLoader,
                taskFactoryMock, dependencyManagerFactoryMock, buildScriptProcessor, pluginRegistry, projectRegistry,
                factory);
        child1 = project.addChildProject("child1", new File("child1"))
        childchild = child1.addChildProject("childchild", new File("childchild"))
        child2 = project.addChildProject("child2", new File("child2"))
        testTask = new DefaultTask(project, TEST_TASK_NAME, null)
    }

    @Test void testProject() {
        assertSame project, child1.parent
        assertSame project, child1.rootProject
        checkProject(project, null, 'root', rootDir)
    }

    private void checkProject(Project project, Project parent, String name, File projectDir) {
        assertSame parent, project.parent
        assertEquals name, project.name
        assertSame(rootDir, project.rootDir)
        assertSame(projectDir, project.projectDir)
        assertSame this.project, project.rootProject
        assertEquals(TEST_BUILD_FILE_NAME, project.buildFileName)
        assertSame project.buildScriptClassLoader, buildScriptClassLoader
        assertSame buildScriptProcessor, project.buildScriptProcessor
        assertNotNull(project.ant)
        assertNotNull(project.convention)
        assertEquals([], project.getDefaultTasks())
        assert project.getTaskFactory().is(taskFactoryMock);
        assert project.dependencyManagerFactory.is(dependencyManagerFactoryMock)
        assert project.dependencies.is(dependencyManagerMock)
        assert pluginRegistry.is(project.pluginRegistry)
        assert project.projectRegistry.getProject(project.path).is(project)
        assert projectRegistry.is(project.projectRegistry)
        assertEquals Project.DEFAULT_ARCHIVES_TASK_BASE_NAME, project.archivesTaskBaseName
        assertEquals project.name, project.archivesBaseName
        assertEquals([] as Set, project.appliedPlugins)
        assertEquals DefaultProject.STATE_CREATED, project.state
        assertEquals DefaultProject.DEFAULT_BUILD_DIR_NAME, project.buildDirName
    }

    @Test public void testAddAndGetAfterEvaluateListener() {
        AfterEvaluateListener afterEvaluateListener1 = {} as AfterEvaluateListener
        AfterEvaluateListener afterEvaluateListener2 = {} as AfterEvaluateListener
        project.addAfterEvaluateListener(afterEvaluateListener1)
        assertEquals(1, project.getAfterEvaluateListeners().size())
        assertEquals([afterEvaluateListener1], project.getAfterEvaluateListeners())
        project.addAfterEvaluateListener(afterEvaluateListener2)
        assertEquals(2, project.getAfterEvaluateListeners().size())
        assertEquals([afterEvaluateListener1, afterEvaluateListener2], project.getAfterEvaluateListeners())
    }

    @Test public void testAddAndGetAfterEvaluateListenerWithClosure() {
        Object testValue = null
        Closure afterEvaluateListener1 = {Project project -> testValue = new Object() }
        project.addAfterEvaluateListener(afterEvaluateListener1)
        assertEquals(1, project.getAfterEvaluateListeners().size())
        project.getAfterEvaluateListeners()[0].afterEvaluate(project)
        assertNotNull(testValue)
    }

    @Test void testEvaluate() {
        boolean afterEvaluate1Called = false;
        boolean afterEvaluate2Called = false;
        Closure afterEvaluate1 = {Project project ->
            afterEvaluate1Called = true
        }
        Closure afterEvaluate2 = {Project project ->
            afterEvaluate2Called = true
        }
        project.addAfterEvaluateListener(afterEvaluate1)
        project.addAfterEvaluateListener(afterEvaluate2)
        BuildScriptProcessor buildScriptProcessorMocker = context.mock(BuildScriptProcessor)
        project.buildScriptProcessor = buildScriptProcessorMocker
        context.checking {
            one(buildScriptProcessorMocker).createScript(project); will(returnValue(testScript))
        }
        assertSame(project, project.evaluate())
        assertEquals(DefaultProject.STATE_INITIALIZED, project.state)
        assert afterEvaluate1Called
        assert afterEvaluate2Called
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

    @Test void testWrapsEvaulationFailure() {
        RuntimeException failure = new RuntimeException()
        BuildScriptProcessor mockBuildScriptProcessor = context.mock(BuildScriptProcessor)
        Script mockScript = context.mock(Script)
        project.buildScriptProcessor = mockBuildScriptProcessor
        context.checking {
            one(mockBuildScriptProcessor).createScript(project)
            will(returnValue(mockScript))
            one(mockScript)
            will(throwException(failure))
        }
        try {
            project.evaluate()
            fail()
        } catch (GradleScriptException e) {
            assertThat(e.originalMessage, equalTo("A problem occurred evaluating project :."))
            assertThat(e.scriptSource, equalTo(project.buildScriptSource))
            assertThat(e.cause, equalTo(failure))
        };
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
        File child1Dir = "child1dir" as File
        File child2Dir = "child2dir" as File
        project.childProjects = [:]
        project.addChildProject('child1', child1Dir)
        assertEquals(1, project.childProjects.size())
        checkProject(project.childProjects.child1, project, 'child1', child1Dir)
        project.addChildProject('child2', child2Dir)
        assertEquals(2, project.childProjects.size())
        checkProject(project.childProjects.child2, project, 'child2', child2Dir)
    }

    @Test public void testDefaultTasks() {
        project.defaultTasks("a", "b");
        assertEquals(["a", "b"], project.getDefaultTasks())
        project.defaultTasks("c");
        assertEquals(["c"], project.getDefaultTasks())
    }

    @Test (expected = InvalidUserDataException) public void testDefaultTasksWithNull() {
        project.defaultTasks(null);
    }

    @Test (expected = InvalidUserDataException) public void testDefaultTasksWithSingleNullValue() {
        project.defaultTasks("a", null);
    }

    @Test public void testCreateTaskWithName() {
        context.checking {
            one(taskFactoryMock).createTask(project, project.tasks, new HashMap(), TEST_TASK_NAME); will(returnValue(testTask))
        }
        assertSame(testTask, project.createTask(TEST_TASK_NAME));
    }

    @Test public void testCreateTaskWithNameAndArgs() {
        Map testArgs = [a: 'b']
        context.checking {
            one(taskFactoryMock).createTask(project, project.tasks, testArgs, TEST_TASK_NAME); will(returnValue(testTask))
        }
        assertSame(testTask, project.createTask(testArgs, TEST_TASK_NAME));
    }

    @Test public void testCreateTaskWithNameAndAction() {
        TaskAction testAction = {} as TaskAction
        context.checking {
            one(taskFactoryMock).createTask(project, project.tasks, new HashMap(), TEST_TASK_NAME); will(returnValue(testTask))
        }
        assertSame(testTask, project.createTask(TEST_TASK_NAME, testAction));
        assertSame(testAction, testTask.getActions()[0])
    }

    @Test public void testCreateTaskWithNameAndClosureAction() {
        Closure testAction = {}
        context.checking {
            one(taskFactoryMock).createTask(project, project.tasks, new HashMap(), TEST_TASK_NAME); will(returnValue(testTask))
        }
        assertSame(testTask, project.createTask(TEST_TASK_NAME, testAction));
        assertEquals(1, testTask.getActions().size())
    }

    @Test public void testCreateTaskWithNameArgsAndActions() {
        Map testArgs = [a: 'b']
        TaskAction testAction = {} as TaskAction
        context.checking {
            one(taskFactoryMock).createTask(project, project.tasks, testArgs, TEST_TASK_NAME); will(returnValue(testTask))
        }
        assertSame(testTask, project.createTask(testArgs, TEST_TASK_NAME, testAction));
        assertSame(testAction, testTask.getActions()[0])
    }

    @Test void testTask() {
        DefaultTask task = project.tasks['task'] = new DefaultTask(project, 'task', null)
        DefaultTask childTask = child1.tasks['task'] = new DefaultTask(child1, 'task', null)
        assertThat(project.task('task'), sameInstance(task))
        assertThat(project.task(':task'), sameInstance(task))
        assertThat(project.task(':child1:task'), sameInstance(childTask))
        assertThat(project.task('child1:task'), sameInstance(childTask))
    }

    @Test void testTaskWithConfigureClosure() {
        Closure testConfigureClosure = {}
        Task mockTask = context.mock(Task)
        project.tasks[TEST_TASK_NAME] = mockTask
        context.checking {
            one(mockTask).configure(testConfigureClosure); will(returnValue(mockTask))
        }

        assertThat(project.task(TEST_TASK_NAME, testConfigureClosure), sameInstance(mockTask))
    }

    @Test void testTaskWithNonExistingTask() {
        try {
            project.task("unknown")
            fail()
        } catch (UnknownTaskException e) {
            assertThat(e.message, equalTo("Task with path 'unknown' could not be found in project ':'."))
        }
    }

    @Test void testTaskWithNonExistingProject() {
        try {
            project.task("unknown:task")
            fail()
        } catch (UnknownTaskException e) {
            assertThat(e.message, equalTo("Task with path 'unknown:task' could not be found in project ':'."))
        }
    }

    @Test void testCanAccessTaskAsAProjectProperty() {
        DefaultTask task = project.tasks[TEST_TASK_NAME] = new DefaultTask(project, TEST_TASK_NAME, null)
        assertThat(project."$TEST_TASK_NAME", sameInstance(task))
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
    //    @Test public void testTaskWithNonDefaultTypeAndTypeInitializer() {
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

        context.checking {
            one(dependencyManagerMock).getBuildResolverDir(); will(returnValue(null))
        }

        assert dependencyManagerMock.is(project.dependencies(testConfigureClosure))
    }


    @Test void testPath() {
        assertEquals(Project.PATH_SEPARATOR + "child1", child1.path)
        assertEquals(Project.PATH_SEPARATOR, project.path)
    }

    @Test void testGetBuildFileCacheName() {
        EmptyScript testScript = new EmptyScript();
        project.setBuildScript(testScript)
        assertEquals(testScript.getClass().getName(), project.getBuildFileClassName())
    }

    @Test void testGetProject() {
        assertSame(project, project.project(Project.PATH_SEPARATOR))
        assertSame(child1, project.project(Project.PATH_SEPARATOR + "child1"))
        assertSame(child1, project.project("child1"))
        assertSame(childchild, child1.project('childchild'))
        assertSame(child1, childchild.project(Project.PATH_SEPARATOR + "child1"))
    }

    @Test void testGetProjectWithUnknownAbsolutePath() {
        try {
            project.project(Project.PATH_SEPARATOR + "unknownchild")
            fail()
        } catch (UnknownProjectException e) {
            assertEquals(e.getMessage(), "Project with path ':unknownchild' could not be found in project ':'.")
        }
    }

    @Test void testGetProjectWithUnknownRelativePath() {
        try {
            project.project("unknownchild")
            fail()
        } catch (UnknownProjectException e) {
            assertEquals(e.getMessage(), "Project with path 'unknownchild' could not be found in project ':'.")
        }
    }

    @Test (expected = InvalidUserDataException) void testGetProjectWithEmptyPath() {
        project.project("")
    }

    @Test (expected = InvalidUserDataException) void testGetProjectWithNullPath() {
        project.project(null)
    }

    @Test void testFindProject() {
        assertSame(project, project.findProject(Project.PATH_SEPARATOR))
        assertSame(child1, project.findProject(Project.PATH_SEPARATOR + "child1"))
        assertSame(child1, project.findProject("child1"))
        assertSame(childchild, child1.findProject('childchild'))
        assertSame(child1, childchild.findProject(Project.PATH_SEPARATOR + "child1"))
    }

    @Test void testFindProjectWithUnknownAbsolutePath() {
        assertNull(project.findProject(Project.PATH_SEPARATOR + "unknownchild"))
    }

    @Test void testFindProjectWithUnknownRelativePath() {
        assertNull(project.findProject("unknownChild"))
    }

    @Test void testGetProjectWithClosure() {
        String newPropValue = 'someValue'
        assert child1.is(project.project("child1") {
            newProp = newPropValue
        })
        assertEquals(child1.newProp, newPropValue)
    }

    @Test void testGetAllTasks() {
        List tasksClean = addTestTaskToAllProjects('clean')
        List tasksCompile = addTestTaskToAllProjects('compile')
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
        Set tasksClean = addTestTaskToAllProjects('clean')
        addTestTaskToAllProjects('compile')
        Set expectedMap = new HashSet()
        assertEquals(tasksClean, project.getTasksByName('clean', true))
        assertEquals([project.tasks['clean']] as Set, project.getTasksByName('clean', false))
    }

    @Test void testGetTasksByNameWithSingularTask() {
        DefaultTask child1Task = child1.tasks['child1Task'] = new DefaultTask(project, 'child1Task', null)
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
        addTestTaskToAllProjects('clean')
        assertEquals(0, project.getTasksByName('cleanXXX', true).size())
        assertEquals(0, project.getTasksByName('cleanXXX', false).size())
    }

    private List addTestTaskToAllProjects(String name) {
        List tasks = []
        project.allprojects.each {Project project ->
            project.tasks[name] = new DefaultTask(project, name, null)
            tasks << project.tasks[name]
        }
        tasks
    }

    @Test void testMethodMissing() {
        DefaultProject dummyParentProject = [scriptMethod: {Closure closure -> 'parent'}] as DefaultProject
        project.parent = dummyParentProject
        boolean closureCalled = false
        Closure testConfigureClosure = {closureCalled = true}
        assertEquals('parent', project.scriptMethod(testConfigureClosure))
        project.tasks['scriptMethod'] = new DefaultTask(project, 'scriptMethod', null)
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
    public void testPropertyMissingWithUnknownProperty() {
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
            assertEquals(child1.getProjectDir(), baseDir)
            assertEquals(expectedValidation, pathValidation)
        }] as BaseDirConverter
        child1.file(expectedPath, PathValidation.FILE)
        assertTrue(converterCalled)

        converterCalled = false
        expectedValidation = PathValidation.NONE
        child1.file(expectedPath)
        assertTrue(converterCalled)
    }

    @Test public void testDir() {
        Task dirTask1 = new Directory(project, 'dir1', null)
        Task dirTask12 = new Directory(project, 'dir1/dir2', null)
        Task dirTask123 = new Directory(project, 'dir1/dir2/dir3', null)
        Map expectedArgMap = WrapUtil.toMap(Task.TASK_TYPE, Directory)
        context.checking {
            one(taskFactoryMock).createTask(project, project.tasks, expectedArgMap, 'dir1'); will(returnValue(dirTask1))
            one(taskFactoryMock).createTask(project, project.tasks, expectedArgMap, 'dir1/dir2'); will(returnValue(dirTask12))
            one(taskFactoryMock).createTask(project, project.tasks, expectedArgMap, 'dir1/dir2/dir3'); will(returnValue(dirTask123))
        }
        assertSame(dirTask123, project.dir('dir1/dir2/dir3'));
    }

    @Test public void testDirWithExistingParentDirTask() {
        Task dirTask1 = new Directory(project, 'dir1', null)
        project.tasks.dir1 = dirTask1
        Task dirTask14 = new Directory(project, 'dir1/dir4', null)
        Map expectedArgMap = WrapUtil.toMap(Task.TASK_TYPE, Directory)
        context.checking {
            one(taskFactoryMock).createTask(project, project.tasks, expectedArgMap, 'dir1/dir4'); will(returnValue(dirTask14))
        }
        project.dir('dir1/dir4');
    }

    @Test (expected = InvalidUserDataException) public void testDirWithConflictingNonDirTask() {
        Task confictingTask = new DefaultTask(project, 'dir1', null)
        project.tasks.dir1 = confictingTask
        Task dirTask14 = new Directory(project, 'dir1/dir4', null)
        Map expectedArgMap = WrapUtil.toMap(Task.TASK_TYPE, Directory)
        context.checking {
            one(taskFactoryMock).createTask(project, project.tasks, expectedArgMap, 'dir1/dir4'); will(returnValue(dirTask14))
        }
        assertSame(dirTask14, project.dir('dir1/dir4'));
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

    @Test void testCompareTo() {
        assertThat(project, lessThan(child1)) 
        assertThat(child1, lessThan(child2))
        assertThat(child1, lessThan(childchild))
        assertThat(child2, lessThan(childchild)) 
    }

    @Test void testDepthCompare() {
        assertTrue(project.depthCompare(child1) < 0)
        assertTrue(child1.depthCompare(project) > 0)
        assertTrue(child1.depthCompare(child2) == 0)
    }

    @Test void testDepth() {
        assertTrue(project.depth == 0)
        assertTrue(child1.depth == 1)
        assertTrue(child2.depth == 1)
        assertTrue(childchild.depth == 2)
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

    @Test void testRelativePath() {
        checkRelativePath(project.&relativePath)
    }

    @Test(expected = GradleException) void testRelativePathWithUncontainedAbsolutePath() {
        File uncontainedAbsoluteFile = new File("abc").absoluteFile;
        project.relativePath(uncontainedAbsoluteFile)
    }

    @Test void testFindRelativePath() {
        checkRelativePath(project.&findRelativePath)
        File uncontainedAbsoluteFile = new File(project.getProjectDir().toString() + "xxx", "abc");
        assertNull(project.findRelativePath(uncontainedAbsoluteFile))
    }

    private def checkRelativePath(Closure pathFinder) {
        String relativePath = 'src/main';
        File relativeFile = new File(relativePath);
        String absoluteFile = new File(project.getProjectDir(), "relativePath").getAbsolutePath();

        println project.getProjectDir()
        println absoluteFile

        assertEquals(relativeFile, pathFinder(relativePath))
        assertEquals(relativeFile, pathFinder(relativeFile))
        assertEquals(new File("relativePath"), pathFinder(absoluteFile))
        assertEquals(new File(""), pathFinder(""))
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

