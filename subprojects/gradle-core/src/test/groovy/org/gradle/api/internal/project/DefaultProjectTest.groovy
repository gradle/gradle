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

import java.awt.Point
import java.text.FieldPosition
import org.apache.tools.ant.types.FileSet
import org.gradle.StartParameter
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.ArtifactHandler
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.dsl.RepositoryHandlerFactory
import org.gradle.api.artifacts.repositories.InternalRepository
import org.gradle.api.file.FileCollection
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.BeanDynamicObject
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandlerFactoryTest
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandlerFactoryTest.ConventionAwareRepositoryHandler
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory
import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.PathResolvingFileCollection
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider
import org.gradle.api.internal.plugins.DefaultConvention
import org.gradle.api.internal.project.AbstractProject.State
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.StandardOutputLogging
import org.gradle.api.plugins.Convention
import org.gradle.api.plugins.ProjectPluginsContainer
import org.gradle.api.tasks.Directory
import org.gradle.configuration.ProjectEvaluator
import org.gradle.groovy.scripts.EmptyScript
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.TestClosure
import org.jmock.integration.junit4.JMock
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.*
import org.gradle.api.internal.project.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.artifacts.Module

/**
 * @author Hans Dockter
 * todo: test for relativeFilePath
 */
@RunWith (JMock.class)
class DefaultProjectTest {
    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    static final String TEST_PROJECT_NAME = 'testproject'

    static final String TEST_BUILD_FILE_NAME = 'build.gradle'

    static final String TEST_TASK_NAME = 'testtask'

    Task testTask;

    DefaultProject project, child1, child2, childchild

    ProjectEvaluator projectEvaluator

    IProjectRegistry projectRegistry

    File rootDir

    Script testScript

    ScriptSource script;

    ServiceRegistry serviceRegistryMock
    ServiceRegistryFactory projectServiceRegistryFactoryMock
    TaskContainerInternal taskContainerMock
    AntBuilderFactory antBuilderFactoryMock
    AntBuilder testAntBuilder

    ConfigurationContainerFactory configurationContainerFactoryMock;
    DefaultConfigurationContainer configurationContainerMock;
    InternalRepository internalRepositoryDummy = context.mock(InternalRepository)
    ResolverFactory resolverFactoryMock = context.mock(ResolverFactory.class);
    RepositoryHandlerFactory repositoryHandlerFactoryMock = context.mock(RepositoryHandlerFactory.class);
    RepositoryHandler repositoryHandlerMock
    DependencyFactory dependencyFactoryMock
    DependencyHandler dependencyHandlerMock = context.mock(DependencyHandler)
    ProjectPluginsContainer projectPluginsHandlerMock = context.mock(ProjectPluginsContainer)
    PublishArtifactFactory publishArtifactFactoryMock = context.mock(PublishArtifactFactory)
    ScriptHandler scriptHandlerMock = context.mock(ScriptHandler)
    DependencyMetaDataProvider dependencyMetaDataProviderMock = context.mock(DependencyMetaDataProvider)
    Gradle build;
    Convention convention = new DefaultConvention();

    StandardOutputRedirector outputRedirectorMock;
    StandardOutputRedirector outputRedirectorOtherProjectsMock;

    @Before
    void setUp() {
        context.imposteriser = ClassImposteriser.INSTANCE
        dependencyFactoryMock = context.mock(DependencyFactory)
        outputRedirectorMock = context.mock(StandardOutputRedirector)
        outputRedirectorOtherProjectsMock = context.mock(StandardOutputRedirector, "otherProjects")
        taskContainerMock = context.mock(TaskContainerInternal);
        antBuilderFactoryMock = context.mock(AntBuilderFactory)
        testAntBuilder = new DefaultAntBuilder()
        context.checking {
            allowing(outputRedirectorOtherProjectsMock).flush();
            allowing(outputRedirectorOtherProjectsMock).off();
            allowing(outputRedirectorOtherProjectsMock).on(withParam(any(LogLevel)));
            allowing(antBuilderFactoryMock).createAntBuilder(); will(returnValue(testAntBuilder))
        }
        configurationContainerMock = context.mock(DefaultConfigurationContainer)
        configurationContainerFactoryMock = [createConfigurationContainer: {
          resolverProvider, dependencyMetaDataProvider, projectDependenciesBuildInstruction ->
            assertSame(build.startParameter.projectDependenciesBuildInstruction, projectDependenciesBuildInstruction)
            configurationContainerMock}] as ConfigurationContainerFactory
        repositoryHandlerMock =  context.mock(DefaultRepositoryHandlerFactoryTest.ConventionAwareRepositoryHandler.class);
        context.checking {
          allowing(repositoryHandlerFactoryMock).createRepositoryHandler(withParam(any(Convention))); will(returnValue(repositoryHandlerMock))
        }
        script = context.mock(ScriptSource.class)
        context.checking {
            allowing(script).getDisplayName(); will(returnValue('[build file]'))
            allowing(script).getClassName(); will(returnValue('scriptClass'))
        }

        testScript = new EmptyScript()
        StartParameter parameter = new StartParameter()
        parameter.pluginPropertiesFile = new File('plugin.properties')

        testTask = HelperUtil.createTask(DefaultTask)
        
        projectEvaluator = context.mock(ProjectEvaluator)
        projectRegistry = new DefaultProjectRegistry()

        projectServiceRegistryFactoryMock = context.mock(ServiceRegistryFactory, 'parent')
        serviceRegistryMock = context.mock(ServiceRegistryFactory, 'project')

        context.checking {
            allowing(projectServiceRegistryFactoryMock).createFor(withParam(notNullValue())); will(returnValue(serviceRegistryMock))
            allowing(serviceRegistryMock).get(TaskContainerInternal); will(returnValue(taskContainerMock))
            allowing(taskContainerMock).getAsDynamicObject(); will(returnValue(new BeanDynamicObject(new TaskContainerDynamicObject(someTask: testTask))))
            allowing(serviceRegistryMock).get(RepositoryHandler); will(returnValue(repositoryHandlerMock))
            allowing(serviceRegistryMock).get(RepositoryHandlerFactory); will(returnValue(repositoryHandlerFactoryMock))
            allowing(serviceRegistryMock).get(ConfigurationContainer); will(returnValue(configurationContainerMock))
            allowing(serviceRegistryMock).get(ArtifactHandler); will(returnValue(context.mock(ArtifactHandler)))
            allowing(serviceRegistryMock).get(DependencyHandler); will(returnValue(dependencyHandlerMock))
            allowing(serviceRegistryMock).get(Convention); will(returnValue(convention))
            allowing(serviceRegistryMock).get(ProjectEvaluator); will(returnValue(projectEvaluator))
            allowing(serviceRegistryMock).get(AntBuilderFactory); will(returnValue(antBuilderFactoryMock))
            allowing(serviceRegistryMock).get(ProjectPluginsContainer); will(returnValue(projectPluginsHandlerMock))
            allowing(serviceRegistryMock).get(ScriptHandler); will(returnValue(scriptHandlerMock))
            allowing(serviceRegistryMock).get(ScriptClassLoaderProvider); will(returnValue(context.mock(ScriptClassLoaderProvider)))
            allowing(serviceRegistryMock).get(StandardOutputRedirector); will(returnValue(outputRedirectorMock))
            allowing(serviceRegistryMock).get(IProjectRegistry); will(returnValue(projectRegistry))
            allowing(serviceRegistryMock).get(DependencyMetaDataProvider); will(returnValue(dependencyMetaDataProviderMock))
        }

        build = context.mock(GradleInternal)

        rootDir = new File("/path/root").absoluteFile
        project = new DefaultProject('root', null, rootDir, new File(rootDir, TEST_BUILD_FILE_NAME), script,
                build, projectServiceRegistryFactoryMock);
        child1 = new DefaultProject("child1", project, new File("child1"), null, script,
                build, projectServiceRegistryFactoryMock)
        project.addChildProject(child1)
        childchild = new DefaultProject("childchild", child1, new File("childchild"), null, script,
                build, projectServiceRegistryFactoryMock)
        child1.addChildProject(childchild)
        child2 = new DefaultProject("child2", project, new File("child2"), null, script,
                build, projectServiceRegistryFactoryMock)
        project.addChildProject(child2)
        [project, child1, childchild, child2].each {
            projectRegistry.addProject(it)
        }
        project.standardOutputRedirector = outputRedirectorMock
        listWithAllChildProjects*.standardOutputRedirector = outputRedirectorOtherProjectsMock
        StandardOutputLogging.off()
    }

  @Test void testRepositories() {
      context.checking {
          allowing(repositoryHandlerFactoryMock).createRepositoryHandler(withParam(any(Convention))); will(returnValue(repositoryHandlerMock))
          ignoring(repositoryHandlerMock)
      }
      assertThat(project.createRepositoryHandler(), sameInstance(repositoryHandlerMock))
  }

  @Ignore void testArtifacts() {
        boolean called = false;
        ArtifactHandler artifactHandlerMock = [testMethod: { called = true }] as ArtifactHandler
        project.artifactHandler = artifactHandlerMock
        project.artifacts {
            testMethod()
        }
        assertTrue(called)
  }

  @Test void testDependencies() {
      context.checking {
          one(dependencyHandlerMock).add('conf', 'dep')
      }
      project.dependencies {
          add('conf', 'dep')
      }
  }

  @Test void testConfigurations() {
        Closure cl = { }
        context.checking {
          one(configurationContainerMock).configure(cl)
        }
        project.configurationContainer = configurationContainerMock
        project.configurations cl
    }

  @Test void testScriptClasspath() {
        context.checking {
            one(scriptHandlerMock).getRepositories()
        }
        project.buildscript {
            repositories
        }
    }

    @Test void testProject() {
        assertSame project, child1.parent
        assertSame project, child1.rootProject
        checkProject(project, null, 'root', rootDir)

        assertNotNull(new DefaultProject('root', null, rootDir, new File(rootDir, TEST_BUILD_FILE_NAME), script,
                build, projectServiceRegistryFactoryMock).standardOutputRedirector)
        assertEquals(TEST_PROJECT_NAME, new DefaultProject(TEST_PROJECT_NAME).name)
    }

    private void checkProject(DefaultProject project, Project parent, String name, File projectDir) {
        assertSame parent, project.parent
        assertEquals name, project.name
        assertEquals Project.DEFAULT_GROUP, project.group
        assertEquals Project.DEFAULT_VERSION, project.version
        assertEquals Project.DEFAULT_STATUS, project.status
        assertSame(rootDir, project.rootDir)
        assertSame(projectDir, project.projectDir)
        assertSame this.project, project.rootProject
        assertEquals(new File(projectDir, TEST_BUILD_FILE_NAME), project.buildFile)
        assertSame projectEvaluator, project.projectEvaluator
        assertSame antBuilderFactoryMock, project.antBuilderFactory
        assertSame project.gradle, build
        assertNotNull(project.ant)
        assertNotNull(project.convention)
        assertEquals([], project.getDefaultTasks())
        assert project.configurations.is(configurationContainerMock)
        assert project.repositoryHandlerFactory.is(repositoryHandlerFactoryMock)
        assertSame(repositoryHandlerMock, project.repositories)
        assert projectRegistry.is(project.projectRegistry)
        assertEquals AbstractProject.State.CREATED, project.state
        assertEquals DefaultProject.DEFAULT_BUILD_DIR_NAME, project.buildDirName
    }

    @Test public void testNullGroupVersionAndStatus() {
        project.version = 'version'
        project.group = 'group'
        project.status = 'status'
        assertEquals('version', project.version)
        assertEquals('group', project.group)
        assertEquals('status', project.status)
        project.version = null
        project.group = null
        project.status = null
        assertEquals(Project.DEFAULT_VERSION, project.version)
        assertEquals(Project.DEFAULT_GROUP, project.group)
        assertEquals(Project.DEFAULT_STATUS, project.status)
    }
    
    @Test public void testExecutesActionBeforeEvaluation() {
        Action<Project> listener = context.mock(Action)
        context.checking {
            one(listener).execute(project)
            one(projectEvaluator).evaluate(project)
        }
        project.beforeEvaluate(listener)
        project.evaluate()
    }

    @Test public void testExecutesActionAfterEvaluation() {
        Action<Project> listener = context.mock(Action)
        context.checking {
            one(projectEvaluator).evaluate(project)
            one(listener).execute(project)
        }
        project.afterEvaluate(listener)
        project.evaluate()
    }

    @Test public void testExecutesClosureBeforeEvaluation() {
        TestClosure listener = context.mock(TestClosure)
        context.checking {
            one(listener).call(project)
            one(projectEvaluator).evaluate(project)
        }

        project.beforeEvaluate(HelperUtil.toClosure(listener))
        project.evaluate()
    }

    @Test public void testExecutesClosureAfterEvaluation() {
        TestClosure listener = context.mock(TestClosure)
        context.checking {
            one(projectEvaluator).evaluate(project)
            one(listener).call(project)
        }

        project.afterEvaluate(HelperUtil.toClosure(listener))
        project.evaluate()
    }

    @Test void testEvaluate() {
        context.checking {
            one(projectEvaluator).evaluate(project)
        }
        assertSame(project, project.evaluate())
        assertEquals(AbstractProject.State.INITIALIZED, project.state)
    }

    @Test void testUsePluginWithString() {
        checkUsePlugin('someplugin')
    }

    @Test void testUsePluginWithClass() {
        checkUsePlugin(Plugin)
    }

    private void checkUsePlugin(def usePluginArgument) {
        context.checking {
            one(projectPluginsHandlerMock).usePlugin(usePluginArgument, project); will(returnValue([:] as Plugin))
        }
        assertThat(project.usePlugin(usePluginArgument), sameInstance(project))
    }

    @Test void testEvaluationDependsOn() {
        boolean mockReader2Finished = false
        boolean mockReader1Called = false
        final ProjectEvaluator mockReader1 = [evaluate: {DefaultProject project ->
            project.evaluationDependsOn(child1.path)
            assertTrue(mockReader2Finished)
            mockReader1Called = true
            testScript
        }] as ProjectEvaluator
        final ProjectEvaluator mockReader2 = [
                evaluate: {DefaultProject project ->
                    mockReader2Finished = true
                    testScript
                }] as ProjectEvaluator
        project.projectEvaluator = mockReader1
        child1.projectEvaluator = mockReader2
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
        final ProjectEvaluator mockReader1 = [evaluate: {DefaultProject project ->
            project.evaluationDependsOn(child1.path)
            testScript
        }] as ProjectEvaluator
        final ProjectEvaluator mockReader2 = [evaluate: {DefaultProject project ->
            project.evaluationDependsOn(project.path)
            testScript
        }] as ProjectEvaluator
        project.projectEvaluator = mockReader1
        child1.projectEvaluator = mockReader2
        project.evaluate()
    }

    @Test void testDependsOnWithNoEvaluation() {
        boolean mockReaderCalled = false
        final ProjectEvaluator mockReader = [evaluateProject: {DefaultProject project ->
            mockReaderCalled = true
            testScript
        }] as ProjectEvaluator
        child1.projectEvaluator = mockReader
        project.dependsOn(child1.name, false)
        assertFalse mockReaderCalled
        assertEquals([child1] as Set, project.dependsOnProjects)
        project.dependsOn(child2.path, false)
        assertEquals([child1, child2] as Set, project.dependsOnProjects)
    }

    @Test void testDependsOn() {
        boolean mockReaderCalled = false
        final ProjectEvaluator mockReader = [evaluate: {DefaultProject project ->
            mockReaderCalled = true
            testScript
        }] as ProjectEvaluator
        child1.projectEvaluator = mockReader
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
        context.checking {
            never(projectEvaluator).evaluate(child1)
        }

        project.dependsOnChildren()
        context.assertIsSatisfied()
        assertTrue(project.dependsOnProjects.contains(child1))
        assertTrue(project.dependsOnProjects.contains(child2))
        assertEquals(2, project.dependsOnProjects.size())
    }

    @Test void testDependsOnChildrenIncludingEvaluate() {
        context.checking {
            one(projectEvaluator).evaluate(child1)
            one(projectEvaluator).evaluate(child2)
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
        ProjectInternal child1 = ['getName': {-> 'child1'}] as ProjectInternal
        ProjectInternal child2 = ['getName': {-> 'child2'}] as ProjectInternal

        project.addChildProject(child1)
        assertEquals(2, project.childProjects.size())
        assertSame(child1, project.childProjects.child1)

        project.addChildProject(child2)
        assertEquals(2, project.childProjects.size())
        assertSame(child2, project.childProjects.child2)
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
            one(taskContainerMock).add([name: TEST_TASK_NAME]); will(returnValue(testTask))
        }
        assertSame(testTask, project.createTask(TEST_TASK_NAME));
    }

    @Test public void testCreateTaskWithNameAndArgs() {
        Map testArgs = [a: 'b']
        context.checking {
            one(taskContainerMock).add(testArgs + [name: TEST_TASK_NAME]); will(returnValue(testTask))
        }
        assertSame(testTask, project.createTask(testArgs, TEST_TASK_NAME));
    }

    @Test public void testCreateTaskWithNameAndAction() {
        Action<Task> testAction = {} as Action
        context.checking {
            one(taskContainerMock).add([name: TEST_TASK_NAME, action: testAction]); will(returnValue(testTask))
        }
        assertSame(testTask, project.createTask(TEST_TASK_NAME, testAction));
    }

    @Test public void testCreateTaskWithNameAndClosureAction() {
        Closure testAction = {}
        context.checking {
            one(taskContainerMock).add([name: TEST_TASK_NAME, action: testAction]); will(returnValue(testTask))
        }
        assertSame(testTask, project.createTask(TEST_TASK_NAME, testAction));
    }

    @Test public void testCreateTaskWithNameArgsAndActions() {
        Map testArgs = [a: 'b']
        Action<Task> testAction = {} as Action
        context.checking {
            one(taskContainerMock).add(testArgs + [name: TEST_TASK_NAME, action: testAction]); will(returnValue(testTask))
        }
        assertSame(testTask, project.createTask(testArgs, TEST_TASK_NAME, testAction));
    }

    @Test void testCanAccessTaskAsAProjectProperty() {
        assertThat(project.someTask, sameInstance(testTask))
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

    @Test void testGetProjectWithUnknownAbsolutePath() {
        try {
            project.project(Project.PATH_SEPARATOR + "unknownchild")
            fail()
        } catch (UnknownProjectException e) {
            assertEquals(e.getMessage(), "Project with path ':unknownchild' could not be found in root project 'root'.")
        }
    }

    @Test void testGetProjectWithUnknownRelativePath() {
        try {
            project.project("unknownchild")
            fail()
        } catch (UnknownProjectException e) {
            assertEquals(e.getMessage(), "Project with path 'unknownchild' could not be found in root project 'root'.")
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

    @Test void testGetAllTasksRecursive() {
        Task projectTask = HelperUtil.createTask(DefaultTask.class)
        Task child1Task = HelperUtil.createTask(DefaultTask.class)
        Task child2Task = HelperUtil.createTask(DefaultTask.class)

        Map expectedMap = new TreeMap()
        expectedMap[project] = [projectTask] as TreeSet
        expectedMap[child1] = [child1Task] as TreeSet
        expectedMap[child2] = [child2Task] as TreeSet
        expectedMap[childchild] = [] as TreeSet

        context.checking {
            one(taskContainerMock).getAll(); will(returnValue([projectTask] as Set))
            one(taskContainerMock).getAll(); will(returnValue([child1Task] as Set))
            one(taskContainerMock).getAll(); will(returnValue([child2Task] as Set))
            one(taskContainerMock).getAll(); will(returnValue([] as Set))
        }
        
        assertEquals(expectedMap, project.getAllTasks(true))
    }

    @Test void testGetAllTasksNonRecursive() {
        Task projectTask = HelperUtil.createTask(DefaultTask.class)

        Map expectedMap = new TreeMap()
        expectedMap[project] = [projectTask] as TreeSet

        context.checking {
            one(taskContainerMock).getAll(); will(returnValue([projectTask] as Set))
        }

        assertEquals(expectedMap, project.getAllTasks(false))
    }

    @Test void testGetTasksByNameRecursive() {
        Task projectTask = HelperUtil.createTask(DefaultTask.class)
        Task child1Task = HelperUtil.createTask(DefaultTask.class)

        context.checking {
            one(taskContainerMock).findByName('task'); will(returnValue(projectTask))
            one(taskContainerMock).findByName('task'); will(returnValue(child1Task))
            one(taskContainerMock).findByName('task'); will(returnValue(null))
            one(taskContainerMock).findByName('task'); will(returnValue(null))
        }

        assertEquals([projectTask, child1Task] as Set, project.getTasksByName('task', true))
    }
    
    @Test void testGetTasksByNameNonRecursive() {
        Task projectTask = HelperUtil.createTask(DefaultTask.class)

        context.checking {
            one(taskContainerMock).findByName('task'); will(returnValue(projectTask))
        }

        assertEquals([projectTask] as Set, project.getTasksByName('task', false))
    }

    @Test (expected = InvalidUserDataException) void testGetTasksWithEmptyName() {
        project.getTasksByName('', true)
    }

    @Test (expected = InvalidUserDataException) void testGetTasksWithNullName() {
        project.getTasksByName(null, true)
    }

    @Test void testGetTasksWithUnknownName() {
        context.checking {
            allowing(taskContainerMock).findByName('task'); will(returnValue(null))
        }

        assertEquals([] as Set, project.getTasksByName('task', true))
        assertEquals([] as Set, project.getTasksByName('task', false))
    }

    private List addTestTaskToAllProjects(String name) {
        List tasks = []
        project.allprojects.each {Project project ->
            tasks << addTestTask(project, name)
        }
        tasks
    }
    
    private Task addTestTask(Project project, String name) {
        new DefaultTask(project, name)
    }

    @Test void testMethodMissing() {
        boolean closureCalled = false
        Closure testConfigureClosure = {closureCalled = true}
        project.someTask(testConfigureClosure)
        assert closureCalled

        project.convention.plugins.test = new TestConvention()
        assertEquals(TestConvention.METHOD_RESULT, project.scriptMethod(testConfigureClosure))
        
        Script projectScript = createScriptForMethodMissingTest('projectScript')
        project.script = projectScript
        assertEquals('projectScript', project.scriptMethod(testConfigureClosure))
    }

    private Script createScriptForMethodMissingTest(String returnValue) {
        String code = """
def scriptMethod(Closure closure) {
    "$returnValue"
}
"""
        HelperUtil.createScript(code)
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
        String propertyName = 'archivesBaseName'
        String expectedValue = 'somename'

        project.archivesBaseName = expectedValue
        project.convention.plugins.test = new TestConvention()
        project.convention.archivesBaseName = 'someothername'
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
        project.convention = new DefaultConvention()
        project."$propertyName" = 4
        assertTrue(project.hasProperty(propertyName))
        assertTrue(child1.hasProperty(propertyName))
    }

    @Test void testProperties() {
        context.checking {
            allowing(dependencyMetaDataProviderMock).getModuleForResolve(); will(returnValue([:] as Module))
            allowing(dependencyMetaDataProviderMock).getModuleForPublicDescriptor(); will(returnValue([:] as Module))
        }
        project.additional = 'additional'

        Map properties = project.properties
        assertEquals(properties.name, 'root')
        assertEquals(properties.additional, 'additional')
        assertSame(properties['someTask'], testTask)
    }

    @Test void testAdditionalProperty() {
        String expectedPropertyName = 'somename'
        String expectedPropertyValue = 'somevalue'
        project.additionalProperties[expectedPropertyName] = expectedPropertyValue
        assertEquals(project."$expectedPropertyName", expectedPropertyValue)
    }

    @Test void testAdditionalPropertiesAreInheritable() {
        project.somename = 'somevalue'
        assertTrue(project.inheritedScope.hasProperty('somename'))
        assertEquals(project.inheritedScope.getProperty('somename'), 'somevalue')
    }

    @Test void testConventionPropertiesAreInheritable() {
        project.convention.plugins.test = new TestConvention()
        project.convention.plugins.test.conv = 'somevalue'
        assertTrue(project.inheritedScope.hasProperty('conv'))
        assertEquals(project.inheritedScope.getProperty('conv'), 'somevalue')
    }

    @Test void testInheritedPropertiesAreInheritable() {
        project.somename = 'somevalue'
        assertTrue(child1.inheritedScope.hasProperty('somename'))
        assertEquals(child1.inheritedScope.getProperty('somename'), 'somevalue')
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
        assertEquals(new File(child1.projectDir, "${Project.DEFAULT_BUILD_DIR_NAME}").canonicalFile, child1.buildDir)
    }

    @Test void testFile() {
        String expectedPath = 'somepath'
        PathValidation expectedValidation = PathValidation.FILE
        boolean converterCalled = false
        child1.fileResolver = [resolve: {String path, PathValidation pathValidation ->
            converterCalled = true
            assertEquals(expectedPath, path)
            assertEquals(expectedValidation, pathValidation)
            child1.projectDir
        }] as FileResolver
        child1.file(expectedPath, PathValidation.FILE)
        assertTrue(converterCalled)

        converterCalled = false
        child1.fileResolver = [resolve: {String path ->
            converterCalled = true
            assertEquals(expectedPath, path)
            child1.projectDir
        }] as FileResolver
        child1.file(expectedPath)
        assertTrue(converterCalled)
    }

    @Test public void testFiles() {
        FileCollection collection = project.files('a', 'b')
        assertThat(collection, instanceOf(PathResolvingFileCollection))
    }

    @Test public void testDir() {
        Task dirTask1 = HelperUtil.createTask(Directory.class)
        Task dirTask12 = HelperUtil.createTask(Directory.class)
        Task dirTask123 = HelperUtil.createTask(Directory.class)
        context.checking {
            one(taskContainerMock).findByName('dir1'); will(returnValue(null))
            one(taskContainerMock).add('dir1', Directory); will(returnValue(dirTask1))
            one(taskContainerMock).findByName('dir1/dir2'); will(returnValue(null))
            one(taskContainerMock).add('dir1/dir2', Directory); will(returnValue(dirTask12))
            one(taskContainerMock).findByName('dir1/dir2/dir3'); will(returnValue(null))
            one(taskContainerMock).add('dir1/dir2/dir3', Directory); will(returnValue(dirTask123))
        }
        assertSame(dirTask123, project.dir('dir1/dir2/dir3'));
    }

    @Test public void testDirWithExistingParentDirTask() {
        Task dirTask1 = HelperUtil.createTask(Directory.class)
        context.checking {
            one(taskContainerMock).findByName('dir1'); will(returnValue(null))
            one(taskContainerMock).add('dir1', Directory); will(returnValue(dirTask1))
        }
        project.dir('dir1')

        Task dirTask14 = HelperUtil.createTask(Directory.class)
        context.checking {
            one(taskContainerMock).findByName('dir1'); will(returnValue(dirTask1))
            one(taskContainerMock).findByName('dir1/dir4'); will(returnValue(null))
            one(taskContainerMock).add('dir1/dir4', Directory); will(returnValue(dirTask14))
        }
        assertSame(dirTask14, project.dir('dir1/dir4'))
    }

    @Test public void testDirWithConflictingNonDirTask() {
        Task dirTask14 = HelperUtil.createTask(DefaultTask.class)

        Task dirTask1 = HelperUtil.createTask(Directory.class)
        context.checking {
            one(taskContainerMock).findByName('dir1'); will(returnValue(null))
            one(taskContainerMock).add('dir1', Directory); will(returnValue(dirTask1))
            one(taskContainerMock).findByName('dir1/dir4'); will(returnValue(dirTask14))
        }

        try {
            project.dir('dir1/dir4')
            fail()
        } catch (InvalidUserDataException e) {
            assertThat(e.message, equalTo("Cannot add directory task 'dir1/dir4' as a non-directory task with this name already exists."))
        }
    }

    @Test void testCachingOfAnt() {
        assertSame(testAntBuilder, project.ant)
        assert project.ant.is(project.ant)
    }

    @Test void testAnt() {
        Closure configureClosure = {fileset(dir: 'dir', id: 'fileset')}
        project.ant(configureClosure)
        assertThat(project.ant.project.getReference('fileset'), instanceOf(FileSet))
    }

    @Test void testCreateAntBuilder() {
        assertSame testAntBuilder, project.createAntBuilder()
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
        checkConfigureProject('configure', [project, child1] as Set)
    }

    @Test void testRelativePath() {
        checkRelativePath(project.&relativePath)
    }

    @Test (expected = GradleException) void testRelativePathWithUncontainedAbsolutePath() {
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

    @Test void testHasUsefulToString() {
        assertEquals('root project \'root\'', project.toString())
        assertEquals('project \':child1\'', child1.toString())
        assertEquals('project \':child1:childchild\'', childchild.toString())
    }

    private void checkConfigureProject(String configureMethod, Set projectsToCheck) {
        String propValue = 'someValue'
        if (configureMethod == 'configure') {
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

    @Test
    void disableStandardOutputCapture() {
        context.checking {
            one(outputRedirectorMock).off()
            one(outputRedirectorMock).flush()
        }
        project.disableStandardOutputCapture()
    }

    @Test
    void captureStandardOutput() {
        context.checking {
            one(outputRedirectorMock).on(LogLevel.DEBUG)
        }
        project.captureStandardOutput(LogLevel.DEBUG)
    }

    @Test
    void configure() {
        Point expectedPoint = new Point(4, 3)
        Point actualPoint = project.configure(new Point()) {
            setLocation(expectedPoint.x, expectedPoint.y)
        }
        assertEquals(expectedPoint, actualPoint)
    }

    @Test(expected = ReadOnlyPropertyException) void setName() {
        project.name = "someNewName" 
    }

    @Test void testGetModule() {
        Module moduleDummyResolve = [:] as Module
        Module moduleDummyPublicDescriptor = [:] as Module
        context.checking {
            allowing(dependencyMetaDataProviderMock).getModuleForResolve(); will(returnValue(moduleDummyResolve))
            allowing(dependencyMetaDataProviderMock).getModuleForPublicDescriptor(); will(returnValue(moduleDummyPublicDescriptor))
        }
        assertThat(project.getModuleForResolve(), equalTo(moduleDummyResolve))
        assertThat(project.getModuleForPublicDescriptor(), equalTo(moduleDummyPublicDescriptor))
    }
}

class TaskContainerDynamicObject {
    Task someTask
    
    def someTask(Closure closure) {
        closure.call()
    }
}

class TestConvention {
    final static String METHOD_RESULT = 'methodResult'
    String name
    String conv
    String archivesBaseName

    def scriptMethod(Closure cl) {
        METHOD_RESULT
    }
}

