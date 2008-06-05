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

/**
 * @author Hans Dockter
 * todo: test for relativeFilePath
 */
class DefaultProjectTest extends GroovyTestCase {
    static final String TEST_PROJECT_NAME = 'testproject'

    static final String TEST_TASK_NAME = 'testtask'

    static final File TEST_ROOT = new File("root")

    static final String TEST_SCRIPT_TEXT = '// somescriptcode'

    DefaultProject project, child1, child2, childchild

    ProjectFactory projectFactory

    BuildScriptProcessor buildScriptProcessor

    BuildScriptFinder buildScriptFinder

    DependencyManager dependencyManager

    PluginRegistry pluginRegistry

    File rootDir

    void setUp() {
        rootDir = new File("/path/root")
        projectFactory = new ProjectFactory(new DefaultDependencyManagerFactory(new File('root')))
        pluginRegistry = new PluginRegistry(new File('somepath'))
        buildScriptProcessor = new BuildScriptProcessor()
        buildScriptFinder = new BuildScriptFinder('buildfile')
        dependencyManager = new DefaultDependencyManager()
        project = new DefaultProject('root', null, rootDir, null, projectFactory, dependencyManager, buildScriptProcessor, buildScriptFinder, pluginRegistry);
        child1 = project.addChildProject("child1")
        childchild = child1.addChildProject("childchild")
        child2 = project.addChildProject("child2")
        //        addScriptToProject(getListWithAllProjects())
    }

    private addScriptToProject(List projects) {
        projects.each {DefaultProject project ->
            project.script = new GroovyShell().parse(TEST_SCRIPT_TEXT)
        }
    }

    void testProject() {
        assertSame(rootDir, project.rootDir)
        assertSame null, project.parent
        assertSame project, project.rootProject
        assertSame projectFactory, project.projectFactory
        assertSame buildScriptProcessor, project.buildScriptProcessor
        assertNotNull(project.ant)
        assertNotNull(project.configureByDag)
        assertNotNull(project.convention)
        assert project.dependencies.is(dependencyManager)
        assert dependencyManager.project.is(project)
        assert pluginRegistry.is(project.pluginRegistry)
        assertEquals 'root', project.name
        assertEquals([:], project.pluginApplyRegistry)
        assertEquals DefaultProject.STATE_CREATED, project.state
        assertEquals DefaultProject.DEFAULT_BUILD_DIR_NAME, project.buildDirName

        assertSame project, child1.parent
        assertSame project, child1.rootProject
    }

    void testEvaluate() {
        final String expectedProp = 'testprop'
        Task nestedTask
        Closure lateInitClosure = {
            nestedTask = project.createTask("$name:addon") {}
        }
        project.createTask(TEST_TASK_NAME, (DefaultProject.TASK_TYPE): TestTask, (DefaultProject.TASK_TYPE_LATE_INITIALIZER): [lateInitClosure])
        // We need a second task to check for cocurrent modification exception 
        project.createTask(TEST_TASK_NAME + "_2", (DefaultProject.TASK_TYPE): TestTask, (DefaultProject.TASK_TYPE_LATE_INITIALIZER): [lateInitClosure])

        createBuildScriptProcessorForEvaluateTests().use(buildScriptProcessor) {
            assertSame(project, project.evaluate())
        }

        assertEquals(DefaultProject.STATE_INITIALIZED, project.state)
        assertTrue(project.tasks[TEST_TASK_NAME].lateInitialized)
        assertTrue(nestedTask.lateInitialized)
    }

    private MockFor createBuildScriptFinderMocker() {
        MockFor buildScriptFinderMocker = new MockFor(BuildScriptFinder)
        buildScriptFinderMocker.demand.getBuildScript(1..1) {DefaultProject project ->
            assert this.project.is(project)
            TEST_SCRIPT_TEXT
        }
        buildScriptFinderMocker
    }

    private MockFor createBuildScriptProcessorForEvaluateTests(Closure evaluateClosure = null) {
        if (!evaluateClosure) {
            evaluateClosure = {DefaultProject project ->
                assert this.project.is(project)
            }
        }
        MockFor buildScriptProcessorMocker = new MockFor(BuildScriptProcessor)
        buildScriptProcessorMocker.demand.evaluate(1..1, evaluateClosure)
        buildScriptProcessorMocker
    }

    void testUsePluginWithString() {
        checkUsePlugin('someplugin')
    }

    void testUsePluginWithClass() {
        checkUsePlugin(JavaPluginTest)
    }

    private void checkUsePlugin(def usePluginArgument) {
        MockFor pluginMocker = new MockFor(Plugin)
        Plugin mockPlugin = [:] as JavaPlugin
        PluginRegistry passedPluginRegistry
        pluginMocker.demand.apply(1..1) {Project project, PluginRegistry pluginRegistry ->
            assert this.project.is(project)
            passedPluginRegistry = pluginRegistry
        }
        MockFor pluginRegistryMocker = new MockFor(PluginRegistry)
        pluginRegistryMocker.demand.getPlugin(1..1) {pluginId ->
            assertEquals(pluginId, usePluginArgument)
            mockPlugin
        }
        pluginRegistryMocker.use(pluginRegistry) {
            pluginMocker.use(mockPlugin) {
                project.usePlugin(usePluginArgument)
            }
        }
        assert passedPluginRegistry.is(pluginRegistry)
        assert project.plugins[0].is(mockPlugin)
        assertEquals(1, project.plugins.size())
    }

    void testUsePluginWithNonExistentPlugin() {
        String unknownPluginName = 'someplugin'
        MockFor pluginRegistryMocker = new MockFor(PluginRegistry)
        pluginRegistryMocker.demand.getPlugin(1..1) {String pluginName ->
            assertEquals(pluginName, unknownPluginName)
            null
        }
        pluginRegistryMocker.use(pluginRegistry) {
            shouldFail(InvalidUserDataException) {
                project.usePlugin(unknownPluginName)
            }
        }
    }

    void testEvaluationDependsOn() {
        boolean mockReader2Finished = false
        boolean mockReader1Called = false
        final BuildScriptProcessor mockReader1 = [evaluate: {DefaultProject project ->
            project.evaluationDependsOn(child1.path)
            assertTrue(mockReader2Finished)
            mockReader1Called = true
        }] as BuildScriptProcessor
        final BuildScriptProcessor mockReader2 = [
                evaluate: {DefaultProject project ->
                    mockReader2Finished = true
                }] as BuildScriptProcessor
        project.buildScriptProcessor = mockReader1
        child1.buildScriptProcessor = mockReader2
        project.evaluate()
        assertTrue mockReader1Called
    }

    void testEvaluationDependsOnWithEmptyArguments() {
        shouldFail(InvalidUserDataException) {
            project.evaluationDependsOn(null)
        }
        shouldFail(InvalidUserDataException) {
            project.evaluationDependsOn('')
        }
    }

    void testEvaluationDependsOnWithCircularDependency() {
        final BuildScriptProcessor mockReader1 = [evaluate: {DefaultProject project ->
            project.evaluationDependsOn(child1.path)
        }] as BuildScriptProcessor
        final BuildScriptProcessor mockReader2 = [evaluate: {DefaultProject project ->
            project.evaluationDependsOn(project.path)
        }] as BuildScriptProcessor
        project.buildScriptProcessor = mockReader1
        child1.buildScriptProcessor = mockReader2
        shouldFail(CircularReferenceException) {
            project.evaluate()
        }
    }

    void testDependsOnWithNoEvaluation() {
        boolean mockReaderCalled = false
        final BuildScriptProcessor mockReader = [evaluate: {DefaultProject project ->
            mockReaderCalled = true
        }] as BuildScriptProcessor
        child1.buildScriptProcessor = mockReader
        project.dependsOn(child1.name, false)
        assertFalse mockReaderCalled
        assertEquals([child1] as Set, project.dependsOnProjects)
        project.dependsOn(child2.path, false)
        assertEquals([child1, child2] as Set, project.dependsOnProjects)
    }

    void testDependsOn() {
        boolean mockReaderCalled = false
        final BuildScriptProcessor mockReader = [evaluate: {DefaultProject project ->
            mockReaderCalled = true
        }] as BuildScriptProcessor
        child1.buildScriptProcessor = mockReader
        project.dependsOn(child1.name)
        assertTrue mockReaderCalled
        assertEquals([child1] as Set, project.dependsOnProjects)

    }

    void testChildrenDependsOnMe() {
        project.childrenDependOnMe()
        assertTrue(child1.dependsOnProjects.contains(project))
        assertTrue(child2.dependsOnProjects.contains(project))
        assertEquals(1, child1.dependsOnProjects.size())
        assertEquals(1, child2.dependsOnProjects.size())
    }

    void testDependsOnChildren() {
        MockFor buildScriptProcessorMocker = new MockFor(BuildScriptProcessor)
        buildScriptProcessorMocker.demand.evaluate(0..0) {DefaultProject project ->}
        buildScriptProcessorMocker.use(child1.buildScriptProcessor) {
            project.dependsOnChildren()
        }
        assertTrue(project.dependsOnProjects.contains(child1))
        assertTrue(project.dependsOnProjects.contains(child2))
        assertEquals(2, project.dependsOnProjects.size())
    }

    void testDependsOnChildrenIncludingEvaluate() {
        MockFor buildScriptProcessorMocker = new MockFor(BuildScriptProcessor)
        Set evaluatedProjects = []
        buildScriptProcessorMocker.demand.evaluate(2..2) {DefaultProject project ->
            evaluatedProjects << project
        }
        buildScriptProcessorMocker.use(child1.buildScriptProcessor) {
            project.dependsOnChildren(true)
        }
        assertTrue(project.dependsOnProjects.contains(child1))
        assertTrue(project.dependsOnProjects.contains(child2))
        assertEquals(evaluatedProjects, project.dependsOnProjects as Set)
        assertEquals(2, project.dependsOnProjects.size())
    }

    void testDependsOnWithIllegalPath() {
        shouldFail(InvalidUserDataException) {
            project.dependsOn(null)
        }
        shouldFail(InvalidUserDataException) {
            project.dependsOn('')
        }
        shouldFail(UnknownProjectException) {
            project.dependsOn(child1.path + 'XXX')
        }
        shouldFail(UnknownProjectException) {
            project.dependsOn(child1.name + 'XXX')
        }
    }

    void testAddAndGetChildProject() {
        DefaultProject dummyParent = [getName: {'/parent'}] as DefaultProject
        DefaultProject dummyChild1 = [getName: {'/child1'}] as DefaultProject
        DefaultProject dummyChild2 = [getName: {'/child2'}] as DefaultProject
        BuildScriptProcessor buildScriptProcessor = [:] as BuildScriptProcessor
        ProjectFactory projectFactory = new ProjectFactory()
        DefaultProject project = new DefaultProject(TEST_PROJECT_NAME, dummyParent, rootDir, dummyParent, projectFactory, dependencyManager, buildScriptProcessor, buildScriptFinder, pluginRegistry);

        StubFor projectFactoryStub = new StubFor(ProjectFactory)
        projectFactoryStub.demand.createProject() {name, parentProject, rootDir, rootProject, factory, processor, finder, registry ->
            assertSame(buildScriptProcessor, processor)
            assert buildScriptFinder.is(finder)
            assert pluginRegistry.is(registry)
            assertSame(projectFactory, factory)
            assertSame(this.dependencyManager, dependencyManager)
            assertSame(project, parentProject)
            assertSame(dummyParent, rootProject)
            assertSame("child1", name)
            assertSame(this.rootDir, rootDir)
            dummyChild1
        }
        projectFactoryStub.demand.createProject() {name, parentProject, rootDir, rootProject, factory, processor, finder, registry -> dummyChild2}
        projectFactoryStub.use(projectFactory) {
            project.addChildProject("child1")
            project.addChildProject("child2")
            projectFactoryStub.expect.verify()
            assertEquals(2, project.getChildProjects().size())
            assertEquals([child1: dummyChild1, child2: dummyChild2], project.getChildProjects())
        }
    }

    public void testCreateTask() {
        DefaultTask task = project.createTask(TEST_TASK_NAME)
        assertEquals(0, task.actions.size())
        checkTask project, task
    }

    public void testCreateTaskWithActions() {
        Closure testaction = {}
        DefaultTask task = project.createTask(TEST_TASK_NAME, testaction)
        assertEquals(TEST_TASK_NAME, task.name)
        assertEquals([testaction], task.actions)
        checkTask project, task
    }

    public void testCreateTaskWithDependencies() {
        List testDependsOn = ['/path1']
        DefaultTask task = project.createTask([dependsOn: testDependsOn], TEST_TASK_NAME)
        assertEquals(TEST_TASK_NAME, task.name)
        assertEquals(testDependsOn as Set, task.dependsOn)
        assert !task.actions
        checkTask project, task
    }

    public void testCreateTaskWithSingleDependency() {
        String testDependsOn = '/path1'
        DefaultTask task = project.createTask([dependsOn: testDependsOn], TEST_TASK_NAME)
        assertEquals(TEST_TASK_NAME, task.name)
        assertEquals([testDependsOn] as Set, task.dependsOn)
        assert !task.actions
        checkTask project, task
    }

    public void testCreateTaskWithActionAndDependencies() {
        List testDependsOn = ['/path1', 'path2', 'path2/path3']
        Closure testaction = {}
        DefaultTask task = project.createTask([dependsOn: testDependsOn], TEST_TASK_NAME, testaction)
        assertEquals(TEST_TASK_NAME, task.name)
        Set testSet = HelperUtil.pureStringTransform(new HashSet(['/path1', "path2", "path2/path3"]))
        assertEquals('dependencies', testSet, task.dependsOn)
        assertEquals('actions', [testaction], task.actions)
        checkTask project, task
    }

    void testCreateDefaultTaskWithSameNameAsExistingTask() {
        Task task = project.createTask(TEST_TASK_NAME)
        shouldFail(InvalidUserDataException) {
            project.createTask(TEST_TASK_NAME)
        }
        Task newTask = project.createTask([overwrite: true], TEST_TASK_NAME)
        assert !newTask.is(task)
    }

    void testCreateTaskWithNonDefaultType() {
        TestTask task = project.createTask(TEST_TASK_NAME, (DefaultProject.TASK_TYPE): TestTask)
        assertEquals(TestTask, project.tasks[TEST_TASK_NAME].class)
        checkTask project, task
    }

    private void checkTask(DefaultProject project, Task task) {
        assertEquals(TEST_TASK_NAME, task.name)
        assertSame(project, task.project)
        assertSame(task, project.task(TEST_TASK_NAME))
    }

    public void testTask() {
        DefaultTask task = project.createTask(TEST_TASK_NAME)
        assert project.task(TEST_TASK_NAME).is(task)
        assert project."$TEST_TASK_NAME".is(task)
    }

    public void testTaskWithConfigureClosure() {
        Closure testConfigureClosure = {}
        DefaultTask task = project.createTask(TEST_TASK_NAME)
        MockFor taskMocker = new MockFor(DefaultTask)
        taskMocker.demand.configure(1..1) {Closure closure ->
            assert closure.is(testConfigureClosure)
            task
        }
        Task resultTask
        taskMocker.use(task) {
            resultTask = project.task(TEST_TASK_NAME, testConfigureClosure)
        }
        assert task.is(resultTask)
    }


    void testTaskWithNonExistingTask() {
        shouldFail(InvalidUserDataException) {
            project.task(TEST_TASK_NAME)
        }
    }

    void testShortCutForTaskCallWithonExistingTask() {
        shouldFail(MissingPropertyException) {
            project.unknownTask
        }
        shouldFail(MissingMethodException) {
            project.unknownTask([dependsOn: '/task2'])
        }
    }

    private List getListWithAllProjects() {
        [project, child1, child2, childchild]
    }

    private List getListWithAllChildProjects() {
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


    public void testDependenciesWithConfigureClosure() {
        Closure testConfigureClosure = {}
        MockFor dependenciesMocker = new MockFor(DependencyManager)
        dependenciesMocker.demand.configure(1..1) {Closure closure ->
            assert closure.is(testConfigureClosure)
            project.dependencies
        }
        DependencyManager resultDependencyManager
        dependenciesMocker.use(project.dependencies) {
            resultDependencyManager = project.dependencies(testConfigureClosure)
        }
        assert resultDependencyManager.is(project.dependencies)
    }


    void testPath() {
        assertEquals(Project.PATH_SEPARATOR + "child1", child1.path)
        assertEquals(Project.PATH_SEPARATOR, project.path)
    }

    void testGetProject() {
        assertSame(project, project.project(Project.PATH_SEPARATOR))
        assertSame(child1, project.project(Project.PATH_SEPARATOR + "child1"))
        assertSame(child1, project.project("child1"))
        assertSame(childchild, child1.project('childchild'))
        assertSame(child1, childchild.project(Project.PATH_SEPARATOR + "child1"))
        shouldFail(UnknownProjectException) {
            project.project(Project.PATH_SEPARATOR + "unknownchild")
        }
        shouldFail(UnknownProjectException) {
            project.project("unknownChild")
        }
    }

    void testGetProjectWithEmptyPath() {
        shouldFail(InvalidUserDataException) {
            project.project("")
        }
        shouldFail(InvalidUserDataException) {
            project.project(null)
        }
    }

     void testGetProjectWithClosure() {
        String newPropValue = 'someValue'
        assert child1.is(project.project("child1") {
            newProp = newPropValue
        })
        assertEquals(child1.newProp, newPropValue)
    }

    void testGetAllTasks() {
        List tasksClean = project.allprojects*.createTask('clean')
        List tasksCompile = project.allprojects*.createTask('compile')
        SortedMap expectedMap = new TreeMap()
        getListWithAllProjects().eachWithIndex {DefaultProject project, int i ->
            expectedMap[project] = new TreeSet([tasksClean[i], tasksCompile[i]])
        }
        assertEquals(expectedMap, project.getAllTasks(true))
        assertEquals(expectedMap.subMap([project]), project.getAllTasks(false))
    }

    void testGetTasks() {
        List tasksClean = project.allprojects*.createTask('clean')
        project.allprojects*.createTask('compile')
        SortedMap expectedMap = new TreeMap()
        getListWithAllProjects().eachWithIndex {DefaultProject project, int i ->
            expectedMap[project] = tasksClean[i]
        }
        assertEquals(expectedMap, project.getTasksByName('clean', true))
        assertEquals([(project): expectedMap[project]] as TreeMap, project.getTasksByName('clean', false))
    }

    void testGetTasksWithSingularTask() {
        DefaultTask child1Task = child1.createTask('child1Task')
        assertEquals([(child1): child1Task] as TreeMap, project.getTasksByName(child1Task.name, true))
        assertEquals(0, project.getTasksByName(child1Task.name, false).size())
    }

    void testGetTasksWithEmptyName() {
        shouldFail(InvalidUserDataException) {
            project.getTasksByName('', true)
        }
        shouldFail(InvalidUserDataException) {
            project.getTasksByName(null, true)
        }
    }

    void testGetTasksWithUnknownName() {
        project.allprojects*.createTask('clean')
        assertEquals(0, project.getTasksByName('cleanXXX', true).size())
        assertEquals(0, project.getTasksByName('cleanXXX', false).size())
    }

    void testMethodMissing() {
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
        project.projectScript = projectScript
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

    void testSetPropertyAndPropertyMissingWithProjectProperty() {
        String propertyName = 'propName'
        String expectedValue = 'somevalue'

        project."$propertyName" = expectedValue
        assertEquals(expectedValue, project."$propertyName")
        assertEquals(expectedValue, child1."$propertyName")
    }

    void testPropertyMissingWithExistingConventionProperty() {
        String propertyName = 'conv'
        String expectedValue = 'somevalue'
        project.convention.plugins.test = new TestConvention()
        project.convention.conv = expectedValue
        assertEquals(expectedValue, project."$propertyName")
        assertEquals(expectedValue, project.convention."$propertyName")
        assertEquals(expectedValue, child1."$propertyName")
    }

    void testSetPropertyAndPropertyMissingWithConventionProperty() {
        String propertyName = 'conv'
        String expectedValue = 'somevalue'
        project.convention.plugins.test = new TestConvention()
        project."$propertyName" = expectedValue
        assertEquals(expectedValue, project."$propertyName")
        assertEquals(expectedValue, project.convention."$propertyName")
        assertEquals(expectedValue, child1."$propertyName")
    }

    void testSetPropertyAndPropertyMissingWithProjectAndConventionProperty() {
        String propertyName = 'name'
        String expectedValue = 'somename'

        project.name = expectedValue
        project.convention.plugins.test = new TestConvention()
        project.convention.name = 'someothername'
        project."$propertyName" = expectedValue
        assertEquals(expectedValue, project."$propertyName")
        assertEquals('someothername', project.convention."$propertyName")
    }

    void testPropertyMissingWithNullProperty() {
        project.nullProp = null
        assertNull(project.nullProp)
        assert project.hasProperty('nullProp')
    }

    void testPropertyMissingWithUnknownProperty() {
        shouldFail(MissingPropertyException) {
            project.unknownProperty
        }
    }

    void testHasProperty() {
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

    void testAdditionalProperty() {
        String expectedPropertyName = 'somename'
        String expectedPropertyValue = 'somevalue'
        project.additionalProperties[expectedPropertyName] = expectedPropertyValue
        assertEquals(project."$expectedPropertyName", expectedPropertyValue)
    }

    void testGetProjectProperty() {
        assert project.is(project.getProject())
    }

    void testRecursive() {
        assertEquals(getListWithAllProjects(), project.allprojects)
    }

    void testChildren() {
        assertEquals(getListWithAllChildProjects(), project.subprojects)
    }

    void testProjectDir() {
        assertEquals(new File("${rootDir.path}/${child1.name}"), child1.projectDir)
    }

    void testBuildDir() {
        assertEquals(new File(child1.projectDir, "${Project.DEFAULT_BUILD_DIR_NAME}"), child1.buildDir)
    }

    void testFile() {
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

    void testDir() {
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

    void testLazyInitOfAnt() {
        assertNotNull(project.ant)
        assert project.ant.is(project.ant)
    }

    void testAnt() {
        Closure configureClosure = {fileset(dir: 'dir')}
        project.ant(configureClosure)
        assertEquals(Closure.OWNER_FIRST, configureClosure.@resolveStrategy)
        assertTrue(project.ant.collectorTarget.children[0].realThing instanceof FileSet)
    }

    void testSubprojects() {
        checkConfigureProject('subprojects', listWithAllChildProjects)
    }

    void testAllprojects() {
        checkConfigureProject('allprojects', listWithAllProjects)
    }

    void testConfigureProjects() {
        checkConfigureProject('configureProjects', [project, child1])
    }

    private void checkConfigureProject(String configureMethod, List projectsToCheck) {
        String propValue = 'someValue'
        if (configureMethod == 'configureProjects') {
            project."$configureMethod" projectsToCheck,
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

