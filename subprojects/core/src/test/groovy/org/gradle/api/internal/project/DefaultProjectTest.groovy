/*
 * Copyright 2010 the original author or authors.
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

import org.apache.tools.ant.types.FileSet
import org.gradle.api.Action
import org.gradle.api.AntBuilder
import org.gradle.api.CircularReferenceException
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState
import org.gradle.api.Task
import org.gradle.api.UnknownProjectException
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.ArtifactHandler
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.component.SoftwareComponentContainer
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.api.internal.FactoryNamedDomainObjectContainer
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.internal.artifacts.Module
import org.gradle.api.internal.artifacts.ProjectBackedModule
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.file.DefaultProjectLayout
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.RootClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.initialization.loadercache.DummyClassLoaderCache
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.project.ant.AntLoggingAdapter
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.provider.ProviderFactory
import org.gradle.configuration.ConfigurationTargetIdentifier
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.configuration.project.ProjectConfigurationActionContainer
import org.gradle.configuration.project.ProjectEvaluator
import org.gradle.groovy.scripts.EmptyScript
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.ProjectAccessListener
import org.gradle.internal.Factory
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.metaobject.BeanDynamicObject
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resource.StringTextResource
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.model.internal.manage.instance.ManagedProxyFactory
import org.gradle.model.internal.manage.schema.ModelSchemaStore
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.normalization.InputNormalizationHandler
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.Path
import org.gradle.util.TestClosure
import org.gradle.util.TestUtil
import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import java.awt.*
import java.lang.reflect.Type
import java.text.FieldPosition

import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith(JMock.class)
class DefaultProjectTest {
    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    static final String TEST_BUILD_FILE_NAME = 'build.gradle'
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    Task testTask;

    DefaultProject project, child1, child2, childchild

    ProjectEvaluator projectEvaluator = context.mock(ProjectEvaluator.class)

    ProjectRegistry projectRegistry

    File rootDir

    groovy.lang.Script testScript

    ScriptSource script = context.mock(ScriptSource.class)

    ServiceRegistry serviceRegistryMock
    ServiceRegistryFactory projectServiceRegistryFactoryMock
    TaskContainerInternal taskContainerMock = context.mock(TaskContainerInternal.class)
    Factory<AntBuilder> antBuilderFactoryMock = context.mock(Factory.class)
    AntBuilder testAntBuilder

    ConfigurationContainer configurationContainerMock = context.mock(ConfigurationContainer.class)
    RepositoryHandler repositoryHandlerMock = context.mock(RepositoryHandler.class)
    DependencyHandler dependencyHandlerMock = context.mock(DependencyHandler)
    ComponentMetadataHandler moduleHandlerMock = context.mock(ComponentMetadataHandler)
    ScriptHandler scriptHandlerMock = context.mock(ScriptHandler)
    DependencyMetaDataProvider dependencyMetaDataProviderMock = context.mock(DependencyMetaDataProvider)
    GradleInternal build = context.mock(GradleInternal)
    ConfigurationTargetIdentifier configurationTargetIdentifier = context.mock(ConfigurationTargetIdentifier)
    FileOperations fileOperationsMock = context.mock(FileOperations)
    ProviderFactory propertyStateFactoryMock = context.mock(ProviderFactory)
    ProcessOperations processOperationsMock = context.mock(ProcessOperations)
    LoggingManagerInternal loggingManagerMock = context.mock(LoggingManagerInternal.class)
    Instantiator instantiatorMock = context.mock(Instantiator)
    SoftwareComponentContainer softwareComponentsMock = context.mock(SoftwareComponentContainer.class)
    InputNormalizationHandler inputNormalizationHandler = context.mock(InputNormalizationHandler.class)
    ProjectConfigurationActionContainer configureActions = context.mock(ProjectConfigurationActionContainer.class)
    PluginManagerInternal pluginManager = context.mock(PluginManagerInternal.class)
    PluginContainer pluginContainer = context.mock(PluginContainer.class)
    ManagedProxyFactory managedProxyFactory = context.mock(ManagedProxyFactory.class)
    AntLoggingAdapter antLoggingAdapter = context.mock(AntLoggingAdapter.class)
    AttributesSchema attributesSchema = context.mock(AttributesSchema)
    BuildOperationExecutor buildOperationExecutor = new TestBuildOperationExecutor()
    CrossProjectConfigurator crossProjectConfigurator = new BuildOperationCrossProjectConfigurator(buildOperationExecutor)

    ClassLoaderScope baseClassLoaderScope = new RootClassLoaderScope(getClass().classLoader, getClass().classLoader, new DummyClassLoaderCache())
    ClassLoaderScope rootProjectClassLoaderScope = baseClassLoaderScope.createChild("root-project")

    @Before
    void setUp() {
        rootDir = new File("/path/root").absoluteFile

        testAntBuilder = new DefaultAntBuilder(null, antLoggingAdapter)

        context.checking {
            allowing(antBuilderFactoryMock).create(); will(returnValue(testAntBuilder))
            allowing(script).getDisplayName(); will(returnValue('[build file]'))
            allowing(script).getClassName(); will(returnValue('scriptClass'))
            allowing(script).getResource(); will(returnValue(new StringTextResource("", "")))
            allowing(scriptHandlerMock).getSourceFile(); will(returnValue(new File(rootDir, TEST_BUILD_FILE_NAME)))
        }

        testScript = new EmptyScript()

        testTask = TestUtil.create(temporaryFolder).task(DefaultTask)

        projectRegistry = new DefaultProjectRegistry()

        projectServiceRegistryFactoryMock = context.mock(ServiceRegistryFactory.class, 'serviceRegistryFactory')
        serviceRegistryMock = context.mock(ServiceRegistry.class, 'serviceRegistry')

        context.checking {
            allowing(projectServiceRegistryFactoryMock).createFor(withParam(notNullValue())); will(returnValue(serviceRegistryMock))
            allowing(serviceRegistryMock).newInstance(TaskContainerInternal); will(returnValue(taskContainerMock))
            allowing(taskContainerMock).getTasksAsDynamicObject(); will(returnValue(new BeanDynamicObject(new TaskContainerDynamicObject(someTask: testTask))))
            allowing(taskContainerMock).all(withParam(notNullValue()))
            allowing(taskContainerMock).whenObjectRemoved(withParam(notNullValue()))
            allowing(serviceRegistryMock).get((Type) RepositoryHandler); will(returnValue(repositoryHandlerMock))
            allowing(serviceRegistryMock).get(ConfigurationContainer); will(returnValue(configurationContainerMock))
            allowing(serviceRegistryMock).get(ArtifactHandler); will(returnValue(context.mock(ArtifactHandler)))
            allowing(serviceRegistryMock).get(DependencyHandler); will(returnValue(dependencyHandlerMock))
            allowing(serviceRegistryMock).get((Type) ComponentMetadataHandler); will(returnValue(moduleHandlerMock))
            allowing(serviceRegistryMock).get((Type) ConfigurationTargetIdentifier); will(returnValue(configurationTargetIdentifier))
            allowing(serviceRegistryMock).get((Type) SoftwareComponentContainer); will(returnValue(softwareComponentsMock))
            allowing(serviceRegistryMock).get((Type) InputNormalizationHandler); will(returnValue(inputNormalizationHandler))
            allowing(serviceRegistryMock).get(ProjectEvaluator); will(returnValue(projectEvaluator))
            allowing(serviceRegistryMock).getFactory(AntBuilder); will(returnValue(antBuilderFactoryMock))
            allowing(serviceRegistryMock).get((Type) ScriptHandler); will(returnValue(scriptHandlerMock))
            allowing(serviceRegistryMock).get((Type) LoggingManagerInternal); will(returnValue(loggingManagerMock))
            allowing(serviceRegistryMock).get(projectRegistryType); will(returnValue(projectRegistry))
            allowing(serviceRegistryMock).get(DependencyMetaDataProvider); will(returnValue(dependencyMetaDataProviderMock))
            allowing(serviceRegistryMock).get(FileResolver); will(returnValue([toString: { -> "file resolver" }] as FileResolver))
            allowing(serviceRegistryMock).get(Instantiator); will(returnValue(instantiatorMock))
            allowing(serviceRegistryMock).get((Type) FileOperations); will(returnValue(fileOperationsMock))
            allowing(serviceRegistryMock).get((Type) ProviderFactory); will(returnValue(propertyStateFactoryMock))
            allowing(serviceRegistryMock).get((Type) ProcessOperations); will(returnValue(processOperationsMock))
            allowing(serviceRegistryMock).get((Type) ScriptPluginFactory); will(returnValue([toString: { -> "script plugin factory" }] as ScriptPluginFactory))
            allowing(serviceRegistryMock).get((Type) ScriptHandlerFactory); will(returnValue([toString: { -> "script plugin factory" }] as ScriptHandlerFactory))
            allowing(serviceRegistryMock).get((Type) ProjectConfigurationActionContainer); will(returnValue(configureActions))
            allowing(serviceRegistryMock).get((Type) PluginManagerInternal); will(returnValue(pluginManager))
            allowing(serviceRegistryMock).get(ManagedProxyFactory); will(returnValue(managedProxyFactory))
            allowing(serviceRegistryMock).get(AttributesSchema) ; will(returnValue(attributesSchema))
            allowing(serviceRegistryMock).get(BuildOperationExecutor) ; will(returnValue(buildOperationExecutor))
            allowing(serviceRegistryMock).get((Type) CrossProjectConfigurator) ; will(returnValue(crossProjectConfigurator))
            allowing(pluginManager).getPluginContainer(); will(returnValue(pluginContainer))

            allowing(serviceRegistryMock).get((Type) DeferredProjectConfiguration); will(returnValue(context.mock(DeferredProjectConfiguration)))
            allowing(serviceRegistryMock).get((Type) ProjectAccessListener); will(returnValue(context.mock(ProjectAccessListener)))

            ITaskFactory taskFactoryMock = context.mock(ITaskFactory)
            allowing(serviceRegistryMock).get(ITaskFactory); will(returnValue(taskFactoryMock))

            ModelRegistry modelRegistry = context.mock(ModelRegistry)
            ignoring(modelRegistry)
            allowing(serviceRegistryMock).get((Type) ModelRegistry); will(returnValue(modelRegistry))
            allowing(serviceRegistryMock).get(ModelRegistry); will(returnValue(modelRegistry))

            ModelSchemaStore modelSchemaStore = context.mock(ModelSchemaStore)
            ignoring(modelSchemaStore)
            allowing(serviceRegistryMock).get((Type) ModelSchemaStore); will(returnValue(modelSchemaStore))
            allowing(serviceRegistryMock).get(ModelSchemaStore); will(returnValue(modelSchemaStore))
            allowing(serviceRegistryMock).get((Type) DefaultProjectLayout); will(returnValue(new DefaultProjectLayout(rootDir, TestFiles.resolver(rootDir))))

            Object listener = context.mock(ProjectEvaluationListener)
            ignoring(listener)
            allowing(build).getProjectEvaluationBroadcaster();
            will(returnValue(listener))

            allowing(build).getParent()
            will(returnValue(null))

            allowing(build).findIdentityPath()
            will(returnValue(Path.ROOT))
            allowing(build).getIdentityPath()
            will(returnValue(Path.ROOT))
            allowing(attributesSchema).attribute(withParam(notNullValue()), withParam(notNullValue()));

            allowing(serviceRegistryMock).get((Type) ObjectFactory); will(returnValue(context.mock(ObjectFactory)))
        }

        AsmBackedClassGenerator classGenerator = new AsmBackedClassGenerator()
        project = classGenerator.newInstance(DefaultProject.class, 'root', null, rootDir, script, build, projectServiceRegistryFactoryMock, rootProjectClassLoaderScope, baseClassLoaderScope);
        def child1ClassLoaderScope = rootProjectClassLoaderScope.createChild("project-child1")
        child1 = classGenerator.newInstance(DefaultProject.class, "child1", project, new File("child1"), script, build, projectServiceRegistryFactoryMock, child1ClassLoaderScope, baseClassLoaderScope)
        project.addChildProject(child1)
        childchild = classGenerator.newInstance(DefaultProject.class, "childchild", child1, new File("childchild"), script, build, projectServiceRegistryFactoryMock, child1ClassLoaderScope.createChild("project-childchild"), baseClassLoaderScope)
        child1.addChildProject(childchild)
        child2 = classGenerator.newInstance(DefaultProject.class, "child2", project, new File("child2"), script, build, projectServiceRegistryFactoryMock, rootProjectClassLoaderScope.createChild("project-child2"), baseClassLoaderScope)
        project.addChildProject(child2)
        [project, child1, childchild, child2].each {
            projectRegistry.addProject(it)
        }
    }

    Type getProjectRegistryType() {
        return DefaultProject.class.getDeclaredMethod("getProjectRegistry").getGenericReturnType()
    }

    //TODO please move more coverage to NewDefaultProjectTest

    @Test
    void testScriptClasspath() {
        context.checking {
            one(scriptHandlerMock).getRepositories()
        }
        project.buildscript {
            repositories
        }
    }

    @Test
    void testProject() {
        assertSame project, child1.parent
        assertSame project, child1.rootProject
        checkProject(project, null, 'root', rootDir)
    }

    private void checkProject(DefaultProject project, Project parent, String name, File projectDir) {
        assertSame parent, project.parent
        assertEquals name, project.name
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
        assertSame(repositoryHandlerMock, project.repositories)
        assert projectRegistry.is(project.projectRegistry)
        assertFalse project.state.executed
        assert project.components.is(softwareComponentsMock)
    }

    @Test
    public void testNullVersionAndStatus() {
        project.version = 'version'
        project.status = 'status'
        assertEquals('version', project.version)
        assertEquals('status', project.status)
        project.version = null
        project.status = null
        assertEquals(Project.DEFAULT_VERSION, project.version)
        assertEquals(Project.DEFAULT_STATUS, project.status)
    }

    @Test
    void testGetGroup() {
        assertThat(project.getGroup(), equalTo(''))
        assertThat(childchild.getGroup(), equalTo('root.child1'))

        child1.group = ''
        assertThat(child1.getGroup(), equalTo(''))

        child1.group = null
        assertThat(child1.getGroup(), equalTo('root'))
    }

    @Test
    public void testExecutesActionBeforeEvaluation() {
        Action<Project> listener = context.mock(Action)
        context.checking {
            one(listener).execute(project)
        }
        project.beforeEvaluate(listener)
        project.projectEvaluationBroadcaster.beforeEvaluate(project)
    }

    @Test
    public void testExecutesActionAfterEvaluation() {
        Action<Project> listener = context.mock(Action)
        context.checking {
            one(listener).execute(project)
        }
        project.afterEvaluate(listener)
        project.projectEvaluationBroadcaster.afterEvaluate(project, null)
    }

    @Test
    public void testExecutesClosureBeforeEvaluation() {
        TestClosure listener = context.mock(TestClosure)
        context.checking {
            one(listener).call(project)
        }

        project.beforeEvaluate(TestUtil.toClosure(listener))
        project.projectEvaluationBroadcaster.beforeEvaluate(project)
    }

    @Test
    public void testExecutesClosureAfterEvaluation() {
        TestClosure listener = context.mock(TestClosure)
        context.checking {
            one(listener).call(project)
        }

        project.afterEvaluate(TestUtil.toClosure(listener))
        project.projectEvaluationBroadcaster.afterEvaluate(project, null)
    }

    @Test
    void testEvaluate() {
        context.checking {
            one(projectEvaluator).evaluate(project, project.state)
        }
        assertSame(project, project.evaluate())
    }

    @Test
    void testEvaluationDependsOn() {
        boolean mockReader2Finished = false
        boolean mockReader1Called = false
        final ProjectEvaluator mockReader1 = [evaluate: { DefaultProject project, state ->
            project.evaluationDependsOn(child1.path)
            assertTrue(mockReader2Finished)
            mockReader1Called = true
            testScript
        }] as ProjectEvaluator
        final ProjectEvaluator mockReader2 = [
            evaluate: { DefaultProject project, state ->
                mockReader2Finished = true
                testScript
            }] as ProjectEvaluator
        project.projectEvaluator = mockReader1
        child1.projectEvaluator = mockReader2
        project.evaluate()
        assertTrue mockReader1Called
        assertTrue mockReader2Finished
    }

    @Test
    void testEvaluationDependsOnChildren() {
        boolean child1MockReaderFinished = false
        boolean child2MockReaderFinished = false
        boolean mockReader1Called = false
        final ProjectEvaluator mockReader1 = [evaluate: { DefaultProject project, state ->
            project.evaluationDependsOnChildren()
            assertTrue(child1MockReaderFinished)
            assertTrue(child2MockReaderFinished)
            mockReader1Called = true
            testScript
        }] as ProjectEvaluator
        final ProjectEvaluator mockReader2 = [
            evaluate: { DefaultProject project, state ->
                child1MockReaderFinished = true
                testScript
            }] as ProjectEvaluator
        final ProjectEvaluator mockReader3 = [
            evaluate: { DefaultProject project, state ->
                child2MockReaderFinished = true
                testScript
            }] as ProjectEvaluator
        project.projectEvaluator = mockReader1
        child1.projectEvaluator = mockReader2
        child2.projectEvaluator = mockReader3
        project.evaluate();
        assertTrue mockReader1Called
    }

    @Test(expected = InvalidUserDataException)
    void testEvaluationDependsOnWithNullArgument() {
        project.evaluationDependsOn(null)
    }

    @Test(expected = InvalidUserDataException)
    void testEvaluationDependsOnWithEmptyArgument() {
        project.evaluationDependsOn('')
    }

    @Test(expected = CircularReferenceException)
    void testEvaluationDependsOnWithCircularDependency() {
        final ProjectEvaluator mockReader1 = [evaluate: { DefaultProject project, ProjectState state ->
            state.executing = true
            project.evaluationDependsOn(child1.path)
            testScript
        }] as ProjectEvaluator
        final ProjectEvaluator mockReader2 = [evaluate: { DefaultProject project, ProjectState state ->
            state.executing = true
            project.evaluationDependsOn(project.path)
            testScript
        }] as ProjectEvaluator
        project.projectEvaluator = mockReader1
        child1.projectEvaluator = mockReader2
        project.evaluate()
    }

    @Test
    void testAddAndGetChildProject() {
        ProjectInternal child1 = ['getName': { -> 'child1' }] as ProjectInternal
        ProjectInternal child2 = ['getName': { -> 'child2' }] as ProjectInternal

        project.addChildProject(child1)
        assertEquals(2, project.childProjects.size())
        assertSame(child1, project.childProjects.child1)

        project.addChildProject(child2)
        assertEquals(2, project.childProjects.size())
        assertSame(child2, project.childProjects.child2)
    }

    @Test
    public void testDefaultTasks() {
        project.defaultTasks("a", "b");
        assertEquals(["a", "b"], project.getDefaultTasks())
        project.defaultTasks("c");
        assertEquals(["c"], project.getDefaultTasks())
    }

    @Test(expected = InvalidUserDataException)
    public void testDefaultTasksWithNull() {
        project.defaultTasks(null);
    }

    @Test(expected = InvalidUserDataException)
    public void testDefaultTasksWithSingleNullValue() {
        project.defaultTasks("a", null);
    }

    @Test
    void testCanAccessTaskAsAProjectProperty() {
        assertThat(project.someTask, sameInstance(testTask))
    }

    @Test(expected = MissingPropertyException)
    void testPropertyShortCutForTaskCallWithNonExistingTask() {
        project.unknownTask
    }

    @Test(expected = groovy.lang.MissingMethodException)
    void testMethodShortCutForTaskCallWithNonExistingTask() {
        project.unknownTask([dependsOn: '/task2'])
    }

    private Set getListWithAllProjects() {
        [project, child1, child2, childchild]
    }

    private Set getListWithAllChildProjects() {
        [child1, child2, childchild]

    }

    @Test
    void testPath() {
        assertEquals(Project.PATH_SEPARATOR + "child1", child1.path)
        assertEquals(Project.PATH_SEPARATOR, project.path)
    }

    @Test
    void testGetProject() {
        assertSame(project, project.project(Project.PATH_SEPARATOR))
        assertSame(child1, project.project(Project.PATH_SEPARATOR + "child1"))
        assertSame(child1, project.project("child1"))
        assertSame(childchild, child1.project('childchild'))
        assertSame(child1, childchild.project(Project.PATH_SEPARATOR + "child1"))
    }

    @Test
    void testGetProjectWithUnknownAbsolutePath() {
        try {
            project.project(Project.PATH_SEPARATOR + "unknownchild")
            fail()
        } catch (UnknownProjectException e) {
            assertEquals(e.getMessage(), "Project with path ':unknownchild' could not be found in root project 'root'.")
        }
    }

    @Test
    void testGetProjectWithUnknownRelativePath() {
        try {
            project.project("unknownchild")
            fail()
        } catch (UnknownProjectException e) {
            assertEquals(e.getMessage(), "Project with path 'unknownchild' could not be found in root project 'root'.")
        }
    }

    @Test(expected = InvalidUserDataException)
    void testGetProjectWithEmptyPath() {
        project.project("")
    }

    @Test(expected = InvalidUserDataException)
    void testGetProjectWithNullPath() {
        project.project(null)
    }

    @Test
    void testFindProject() {
        assertSame(project, project.findProject(Project.PATH_SEPARATOR))
        assertSame(child1, project.findProject(Project.PATH_SEPARATOR + "child1"))
        assertSame(child1, project.findProject("child1"))
        assertSame(childchild, child1.findProject('childchild'))
        assertSame(child1, childchild.findProject(Project.PATH_SEPARATOR + "child1"))
    }

    @Test
    void testFindProjectWithUnknownAbsolutePath() {
        assertNull(project.findProject(Project.PATH_SEPARATOR + "unknownchild"))
    }

    @Test
    void testFindProjectWithUnknownRelativePath() {
        assertNull(project.findProject("unknownChild"))
    }

    @Test
    void testGetProjectWithClosure() {
        String newPropValue = 'someValue'
        assert child1.is(project.project("child1") {
            ext.newProp = newPropValue
        })
        assertEquals(child1.newProp, newPropValue)
    }

    @Test
    void testGetProjectWithAction() {
        def child1 = project.project("child1")
        def action = context.mock(Action)
        context.checking {
            one(action).execute(child1)
        }
        assert child1 == project.project("child1", action)
    }

    @Test
    void testMethodMissing() {
        boolean closureCalled = false
        Closure testConfigureClosure = { closureCalled = true }
        project.someTask(testConfigureClosure)
        assert closureCalled

        project.convention.plugins.test = new TestConvention()
        assertEquals(TestConvention.METHOD_RESULT, project.scriptMethod(testConfigureClosure))

        project.script = createScriptForMethodMissingTest('projectScript')
        assertEquals('projectScript', project.scriptMethod(testConfigureClosure))
    }

    private groovy.lang.Script createScriptForMethodMissingTest(String returnValue) {
        String code = """
def scriptMethod(Closure closure) {
    "$returnValue"
}
"""
        TestUtil.createScript(code)
    }

    @Test
    void testSetPropertyAndPropertyMissingWithProjectProperty() {
        String propertyName = 'propName'
        String expectedValue = 'somevalue'

        project.ext."$propertyName" = expectedValue
        assertEquals(expectedValue, project."$propertyName")
        assertEquals(expectedValue, child1."$propertyName")
    }

    @Test
    void testPropertyMissingWithExistingConventionProperty() {
        String propertyName = 'conv'
        String expectedValue = 'somevalue'
        project.convention.plugins.test = new TestConvention()
        project.convention.conv = expectedValue
        assertEquals(expectedValue, project."$propertyName")
        assertEquals(expectedValue, project.convention."$propertyName")
        assertEquals(expectedValue, child1."$propertyName")
    }

    @Test
    void testSetPropertyAndPropertyMissingWithConventionProperty() {
        String expectedValue = 'somevalue'
        project.convention.plugins.test = new TestConvention()
        project.conv = expectedValue
        assertEquals(expectedValue, project.conv)
        assertEquals(expectedValue, project.convention.plugins.test.conv)
        assertEquals(expectedValue, child1.conv)
    }

    @Test
    void testSetPropertyAndPropertyMissingWithProjectAndConventionProperty() {
        String propertyName = 'archivesBaseName'
        String expectedValue = 'somename'

        project.ext.archivesBaseName = expectedValue
        project.convention.plugins.test = new TestConvention()
        project.convention.archivesBaseName = 'someothername'
        project."$propertyName" = expectedValue
        assertEquals(expectedValue, project."$propertyName")
        assertEquals('someothername', project.convention."$propertyName")
    }

    @Test
    void testPropertyMissingWithNullProperty() {
        project.ext.nullProp = null
        assertNull(project.nullProp)
        assert project.hasProperty('nullProp')
    }

    @Test
    void testFindProperty() {
        project.ext.someProp = "somePropValue"
        assert project.findProperty('someProp') == "somePropValue"
        assertNull(project.findProperty("someNonExistingProp"))
    }

    @Test(expected = MissingPropertyException)
    public void testPropertyMissingWithUnknownProperty() {
        project.unknownProperty
    }

    @Test
    void testHasProperty() {
        assertTrue(project.hasProperty('name'))
        String propertyName = 'beginIndex'
        assertFalse(project.hasProperty(propertyName))
        assertFalse(child1.hasProperty(propertyName))

        project.convention.plugins.test = new FieldPosition(0)
        project."$propertyName" = 5
        assertTrue(project.hasProperty(propertyName))
        assertTrue(child1.hasProperty(propertyName))
    }

    @Test
    void testProperties() {
        context.checking {
            allowing(dependencyMetaDataProviderMock).getModule(); will(returnValue({} as Module))
            ignoring(fileOperationsMock)
            ignoring(propertyStateFactoryMock)
            ignoring(taskContainerMock)
            allowing(serviceRegistryMock).get(ServiceRegistryFactory); will(returnValue({} as ServiceRegistryFactory))
        }
        project.ext.additional = 'additional'

        Map properties = project.properties
        assertEquals(properties.name, 'root')
        assertEquals(properties.additional, 'additional')
        assertSame(properties['someTask'], testTask)
    }

    @Test
    void testExtraPropertiesAreInheritable() {
        project.ext.somename = 'somevalue'
        assertTrue(project.inheritedScope.hasProperty('somename'))
        assertEquals(project.inheritedScope.getProperty('somename'), 'somevalue')
    }

    @Test
    void testConventionPropertiesAreInheritable() {
        project.convention.plugins.test = new TestConvention()
        project.convention.plugins.test.conv = 'somevalue'
        assertTrue(project.inheritedScope.hasProperty('conv'))
        assertEquals(project.inheritedScope.getProperty('conv'), 'somevalue')
    }

    @Test
    void testInheritedPropertiesAreInheritable() {
        project.ext.somename = 'somevalue'
        assertTrue(child1.inheritedScope.hasProperty('somename'))
        assertEquals(child1.inheritedScope.getProperty('somename'), 'somevalue')
    }

    @Test
    void testGetProjectProperty() {
        assert project.is(project.getProject())
    }

    @Test
    void testAllprojectsField() {
        assertEquals(getListWithAllProjects(), project.allprojects)
    }

    @Test
    void testChildren() {
        assertEquals(getListWithAllChildProjects(), project.subprojects)
    }

    @Test
    void testBuildDir() {
        assertEquals(new File(rootDir, "build"), project.buildDir)

        project.buildDir = "abc"
        assertEquals(new File(rootDir, "abc"), child1.buildDir)
    }

    @Test
    void testCachingOfAnt() {
        assertSame(testAntBuilder, project.ant)
        assert project.ant.is(project.ant)
    }

    @Test
    void testAnt() {
        Closure configureClosure = { fileset(dir: 'dir', id: 'fileset') }
        project.ant(configureClosure)
        assertThat(project.ant.project.getReference('fileset'), instanceOf(FileSet))
    }

    @Test
    void testCreateAntBuilder() {
        assertSame testAntBuilder, project.createAntBuilder()
    }

    @Test
    void testCompareTo() {
        assertThat(project, lessThan(child1))
        assertThat(child1, lessThan(child2))
        assertThat(child1, lessThan(childchild))
        assertThat(child2, lessThan(childchild))
    }

    @Test
    void testDepthCompare() {
        assertTrue(project.depthCompare(child1) < 0)
        assertTrue(child1.depthCompare(project) > 0)
        assertTrue(child1.depthCompare(child2) == 0)
    }

    @Test
    void testDepth() {
        assertTrue(project.depth == 0)
        assertTrue(child1.depth == 1)
        assertTrue(child2.depth == 1)
        assertTrue(childchild.depth == 2)
    }

    @Test
    void testSubprojects() {
        checkConfigureProject('subprojects', listWithAllChildProjects)
    }

    @Test
    void testAllprojects() {
        checkConfigureProject('allprojects', listWithAllProjects)
    }

    @Test
    void testConfigureProjects() {
        checkConfigureProject('configure', [project, child1] as Set)
    }

    private void checkConfigureProject(String configureMethod, Set projectsToCheck) {
        String propValue = 'someValue'
        if (configureMethod == 'configure') {
            project."$configureMethod" projectsToCheck as java.util.List,
                {
                    ext.testSubProp = propValue
                }
        } else {
            project."$configureMethod"(
                {
                    ext.testSubProp = propValue
                })
        }

        projectsToCheck.each {
            assertEquals(propValue, it.testSubProp)
        }
    }

    @Test
    void configure() {
        Point expectedPoint = new Point(4, 3)
        Point actualPoint = project.configure(new Point()) {
            setLocation(expectedPoint.x, expectedPoint.y)
        }
        assertEquals(expectedPoint, actualPoint)
    }

    @Test()
    void setName() {
        try {
            project.name = "someNewName"
            fail()
        } catch (GroovyRuntimeException e) {
            assertThat(e.message, equalTo("Cannot set the value of read-only property 'name' for root project 'root' of type ${Project.name}." as String))
        }
    }

    @Test
    void testGetModule() {
        Module moduleDummyResolve = new ProjectBackedModule(project)
        context.checking {
            allowing(dependencyMetaDataProviderMock).getModule(); will(returnValue(moduleDummyResolve))
        }
        assertThat(project.getModule(), equalTo(moduleDummyResolve))
    }

    @Test
    void convertsAbsolutePathToAbsolutePath() {
        assertThat(project.absoluteProjectPath(':'), equalTo(':'))
        assertThat(project.absoluteProjectPath(':other'), equalTo(':other'))
        assertThat(child1.absoluteProjectPath(':'), equalTo(':'))
        assertThat(child1.absoluteProjectPath(':other'), equalTo(':other'))
    }

    @Test
    void convertsRelativePathToAbsolutePath() {
        assertThat(project.absoluteProjectPath('task'), equalTo(':task'))
        assertThat(project.absoluteProjectPath('sub:other'), equalTo(':sub:other'))
        assertThat(child1.absoluteProjectPath('task'), equalTo(':child1:task'))
        assertThat(child1.absoluteProjectPath('sub:other'), equalTo(':child1:sub:other'))
    }

    @Test
    void convertsRelativePathToRelativePath() {
        assertThat(project.relativeProjectPath('task'), equalTo('task'))
        assertThat(project.relativeProjectPath('sub:other'), equalTo('sub:other'))
    }

    @Test
    void convertsAbsolutePathToRelativePath() {
        assertThat(project.relativeProjectPath(':'), equalTo(':'))
        assertThat(project.relativeProjectPath(':task'), equalTo('task'))
        assertThat(project.relativeProjectPath(':sub:other'), equalTo('sub:other'))
        assertThat(child1.relativeProjectPath(':child1'), equalTo(':child1'))
        assertThat(child1.relativeProjectPath(':child1:task'), equalTo('task'))
        assertThat(child1.relativeProjectPath(':child12:task'), equalTo(':child12:task'))
        assertThat(child1.relativeProjectPath(':sub:other'), equalTo(':sub:other'))
    }

    @Test
    void createsADomainObjectContainer() {
        def container = context.mock(FactoryNamedDomainObjectContainer)
        context.checking {
            allowing(instantiatorMock).newInstance(withParam(equalTo(FactoryNamedDomainObjectContainer)), withParam(notNullValue()))
            will(returnValue(container))
        }
        assertThat(project.container(String.class), sameInstance(container))
        assertThat(project.container(String.class, context.mock(NamedDomainObjectFactory.class)), sameInstance(container))
        assertThat(project.container(String.class, {}), sameInstance(container))
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

