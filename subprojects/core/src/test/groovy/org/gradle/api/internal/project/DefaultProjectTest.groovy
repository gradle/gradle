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
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.Task
import org.gradle.api.UnknownProjectException
import org.gradle.api.artifacts.dsl.ArtifactHandler
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.DependencyLockingHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.component.SoftwareComponentContainer
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.FactoryNamedDomainObjectContainer
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.internal.build.ProjectBuildProvider
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.internal.file.DefaultProjectLayout
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.RootClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.initialization.loadercache.DummyClassLoaderCache
import org.gradle.api.internal.model.DefaultObjectFactory
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.project.ant.AntLoggingAdapter
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.api.internal.provider.DefaultPropertyFactory
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.internal.resources.ApiTextResourceAdapter
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyUsageTracker
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.provider.ProviderFactory
import org.gradle.configuration.ConfigurationTargetIdentifier
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.configuration.internal.DynamicCallContextTracker
import org.gradle.configuration.internal.ListenerBuildOperationDecorator
import org.gradle.configuration.internal.TestListenerBuildOperationDecorator
import org.gradle.configuration.project.ProjectConfigurationActionContainer
import org.gradle.configuration.project.ProjectEvaluator
import org.gradle.groovy.scripts.EmptyScript
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.ClassLoaderScopeRegistryListener
import org.gradle.internal.Actions
import org.gradle.internal.Factory
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.management.DependencyResolutionManagementInternal
import org.gradle.internal.metaobject.BeanDynamicObject
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resource.StringTextResource
import org.gradle.internal.resource.TextFileResourceLoader
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.invocation.GradleLifecycleActionExecutor
import org.gradle.model.internal.manage.instance.ManagedProxyFactory
import org.gradle.model.internal.manage.schema.ModelSchemaStore
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.normalization.internal.InputNormalizationHandlerInternal
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.gradle.util.TestClosure
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import java.lang.reflect.Type
import java.text.FieldPosition
import java.util.function.Consumer

class DefaultProjectTest extends Specification {

    static final String TEST_BUILD_FILE_NAME = 'build.gradle'

    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    Task testTask

    DefaultProject project, child1, child2, childchild
    ProjectState projectState, child1State, child2State, chilchildState

    ProjectEvaluator projectEvaluator = Mock(ProjectEvaluator)

    ProjectRegistry projectRegistry

    File rootDir
    File buildFile

    groovy.lang.Script testScript

    ScriptSource script = Stub(ScriptSource)

    ServiceRegistry serviceRegistryMock
    ServiceRegistryFactory projectServiceRegistryFactoryMock
    TaskContainerInternal taskContainerMock = Stub(TaskContainerInternal)
    Factory<AntBuilder> antBuilderFactoryMock = Stub(Factory)
    AntBuilder testAntBuilder

    RoleBasedConfigurationContainerInternal configurationContainerMock = Stub(RoleBasedConfigurationContainerInternal)
    RepositoryHandler repositoryHandlerMock = Stub(RepositoryHandler)
    DependencyHandler dependencyHandlerMock = Stub(DependencyHandler)
    DependencyFactory dependencyFactoryMock = Stub(DependencyFactory)
    ComponentMetadataHandler moduleHandlerMock = Stub(ComponentMetadataHandler)
    ScriptHandlerInternal scriptHandlerMock = Mock(ScriptHandlerInternal)
    GradleInternal build = Stub(GradleInternal)
    ConfigurationTargetIdentifier configurationTargetIdentifier = Stub(ConfigurationTargetIdentifier)
    FileOperations fileOperationsMock = Stub(FileOperations)
    ProviderFactory propertyStateFactoryMock = Stub(ProviderFactory)
    ProcessOperations processOperationsMock = Stub(ProcessOperations)
    LoggingManagerInternal loggingManagerMock = Stub(LoggingManagerInternal)
    Instantiator instantiatorMock = Stub(Instantiator) {
        newInstance(LifecycleAwareProject, _, _, _) >> { args ->
            def params = args[1]
            new LifecycleAwareProject(params[0], params[1], params[2])
        }
    }
    SoftwareComponentContainer softwareComponentsMock = Stub(SoftwareComponentContainer)
    InputNormalizationHandlerInternal inputNormalizationHandler = Stub(InputNormalizationHandlerInternal)
    ProjectConfigurationActionContainer configureActions = Stub(ProjectConfigurationActionContainer)
    PluginManagerInternal pluginManager = Stub(PluginManagerInternal)
    PluginContainer pluginContainer = Stub(PluginContainer)
    ManagedProxyFactory managedProxyFactory = Stub(ManagedProxyFactory)
    AntLoggingAdapter antLoggingAdapter = Stub(AntLoggingAdapter)
    AttributesSchema attributesSchema = Stub(AttributesSchema)
    TextFileResourceLoader textResourceLoader = Stub(TextFileResourceLoader)
    ApiTextResourceAdapter.Factory textResourceAdapterFactory = Stub(ApiTextResourceAdapter.Factory)
    BuildOperationRunner buildOperationRunner = new TestBuildOperationRunner()
    ListenerBuildOperationDecorator listenerBuildOperationDecorator = new TestListenerBuildOperationDecorator()
    DependencyResolutionManagementInternal dependencyResolutionManagement = Stub(DependencyResolutionManagementInternal)
    CrossProjectConfigurator crossProjectConfigurator = new BuildOperationCrossProjectConfigurator(buildOperationRunner)
    ClassLoaderScope baseClassLoaderScope = new RootClassLoaderScope("root", getClass().classLoader, getClass().classLoader, new DummyClassLoaderCache(), Stub(ClassLoaderScopeRegistryListener))
    ClassLoaderScope rootProjectClassLoaderScope = baseClassLoaderScope.createChild("root-project", null)
    ObjectFactory objectFactory = new DefaultObjectFactory(instantiatorMock, Stub(NamedObjectInstantiator), Stub(DirectoryFileTreeFactory), TestFiles.patternSetFactory, new DefaultPropertyFactory(Stub(PropertyHost)), Stub(FilePropertyFactory), TestFiles.taskDependencyFactory(), Stub(FileCollectionFactory), Stub(DomainObjectCollectionFactory))
    GradleLifecycleActionExecutor eagerLifecycleExecutor = Stub(GradleLifecycleActionExecutor)

    def setup() {
        rootDir = new File("/path/root").absoluteFile
        buildFile = new File(rootDir, TEST_BUILD_FILE_NAME)

        testAntBuilder = new DefaultAntBuilder(null, antLoggingAdapter)

        antBuilderFactoryMock.create() >> testAntBuilder
        script.getDisplayName() >> '[build file]'
        script.getClassName() >> 'scriptClass'
        script.getResource() >> new StringTextResource("", "")
        scriptHandlerMock.getSourceFile() >> buildFile

        testScript = new EmptyScript()

        testTask = TestUtil.create(temporaryFolder).task(DefaultTask)

        projectRegistry = new DefaultProjectRegistry()

        projectServiceRegistryFactoryMock = Stub(ServiceRegistryFactory)
        serviceRegistryMock = Stub(ServiceRegistry)

        projectServiceRegistryFactoryMock.createFor({ it != null }) >> serviceRegistryMock
        serviceRegistryMock.get(TaskContainerInternal) >> taskContainerMock
        taskContainerMock.getTasksAsDynamicObject() >> new BeanDynamicObject(new TaskContainerDynamicObject(someTask: testTask))
        serviceRegistryMock.get((Type) RepositoryHandler) >> repositoryHandlerMock
        serviceRegistryMock.get(RoleBasedConfigurationContainerInternal) >> configurationContainerMock
        serviceRegistryMock.get(ArtifactHandler) >> Stub(ArtifactHandler)
        serviceRegistryMock.get(DependencyHandler) >> dependencyHandlerMock
        serviceRegistryMock.get(DependencyFactory) >> dependencyFactoryMock
        serviceRegistryMock.get((Type) ComponentMetadataHandler) >> moduleHandlerMock
        serviceRegistryMock.get((Type) ConfigurationTargetIdentifier) >> configurationTargetIdentifier
        serviceRegistryMock.get((Type) SoftwareComponentContainer) >> softwareComponentsMock
        serviceRegistryMock.get((Type) InputNormalizationHandlerInternal) >> inputNormalizationHandler
        serviceRegistryMock.get(ProjectEvaluator) >> projectEvaluator
        serviceRegistryMock.get(DynamicLookupRoutine) >> new DefaultDynamicLookupRoutine()
        serviceRegistryMock.getFactory(AntBuilder) >> antBuilderFactoryMock
        serviceRegistryMock.get((Type) ScriptHandlerInternal) >> scriptHandlerMock
        serviceRegistryMock.get((Type) LoggingManagerInternal) >> loggingManagerMock
        serviceRegistryMock.get(FileResolver) >> Stub(FileResolver)
        serviceRegistryMock.get(CollectionCallbackActionDecorator) >> Stub(CollectionCallbackActionDecorator)
        serviceRegistryMock.get(Instantiator) >> instantiatorMock
        serviceRegistryMock.get(InstantiatorFactory) >> TestUtil.instantiatorFactory()
        serviceRegistryMock.get((Type) FileOperations) >> fileOperationsMock
        serviceRegistryMock.get((Type) ProviderFactory) >> propertyStateFactoryMock
        serviceRegistryMock.get((Type) ProcessOperations) >> processOperationsMock
        serviceRegistryMock.get((Type) ScriptPluginFactory) >> Stub(ScriptPluginFactory)
        serviceRegistryMock.get((Type) ScriptHandlerFactory) >> Stub(ScriptHandlerFactory)
        serviceRegistryMock.get((Type) ProjectConfigurationActionContainer) >> configureActions
        serviceRegistryMock.get((Type) PluginManagerInternal) >> pluginManager
        serviceRegistryMock.get((Type) TextFileResourceLoader) >> textResourceLoader
        serviceRegistryMock.get((Type) ApiTextResourceAdapter.Factory) >> textResourceAdapterFactory
        serviceRegistryMock.get(ManagedProxyFactory) >> managedProxyFactory
        serviceRegistryMock.get(AttributesSchema) >> attributesSchema
        serviceRegistryMock.get(BuildOperationRunner) >> buildOperationRunner
        serviceRegistryMock.get((Type) ListenerBuildOperationDecorator) >> listenerBuildOperationDecorator
        serviceRegistryMock.get((Type) CrossProjectConfigurator) >> crossProjectConfigurator
        serviceRegistryMock.get(DependencyResolutionManagementInternal) >> dependencyResolutionManagement
        serviceRegistryMock.get(DomainObjectCollectionFactory) >> TestUtil.domainObjectCollectionFactory()
        serviceRegistryMock.get(CrossProjectModelAccess) >> new DefaultCrossProjectModelAccess(projectRegistry, instantiatorMock, eagerLifecycleExecutor)
        serviceRegistryMock.get(ObjectFactory) >> objectFactory
        serviceRegistryMock.get(TaskDependencyFactory) >> DefaultTaskDependencyFactory.forProject(taskContainerMock, Mock(TaskDependencyUsageTracker))
        pluginManager.getPluginContainer() >> pluginContainer

        serviceRegistryMock.get((Type) DeferredProjectConfiguration) >> Stub(DeferredProjectConfiguration)

        serviceRegistryMock.get(ITaskFactory) >> Stub(ITaskFactory)

        ModelRegistry modelRegistry = Stub(ModelRegistry)
        serviceRegistryMock.get((Type) ModelRegistry) >> modelRegistry
        serviceRegistryMock.get(ModelRegistry) >> modelRegistry

        ModelSchemaStore modelSchemaStore = Stub(ModelSchemaStore)
        serviceRegistryMock.get((Type) ModelSchemaStore) >> modelSchemaStore
        serviceRegistryMock.get(ModelSchemaStore) >> modelSchemaStore
        serviceRegistryMock.get((Type) DefaultProjectLayout) >> new DefaultProjectLayout(rootDir, TestFiles.resolver(rootDir), Stub(TaskDependencyFactory), Stub(Factory), Stub(PropertyHost), Stub(FileCollectionFactory), TestFiles.filePropertyFactory(), TestFiles.fileFactory())

        build.getProjectEvaluationBroadcaster() >> Stub(ProjectEvaluationListener)
        build.getParent() >> null
        build.isRootBuild() >> true
        build.getIdentityPath() >> Path.ROOT

        serviceRegistryMock.get((Type) ObjectFactory) >> Stub(ObjectFactory)
        serviceRegistryMock.get((Type) DependencyLockingHandler) >> Stub(DependencyLockingHandler)
        serviceRegistryMock.get((Type) DynamicCallContextTracker) >> Stub(DynamicCallContextTracker)

        projectState = Mock(ProjectState)
        projectState.name >> 'root'
        project = defaultProject('root', projectState, null, rootDir, rootProjectClassLoaderScope)
        def child1ClassLoaderScope = rootProjectClassLoaderScope.createChild("project-child1", null)
        child1State = Mock(ProjectState)
        child1 = defaultProject("child1", child1State, project, new File("child1"), child1ClassLoaderScope)
        child1State.mutableModel >> child1
        child1State.name >> "child1"
        chilchildState = Mock(ProjectState)
        childchild = defaultProject("childchild", chilchildState, child1, new File("childchild"), child1ClassLoaderScope.createChild("project-childchild", null))
        child2State = Mock(ProjectState)
        child2 = defaultProject("child2", child2State, project, new File("child2"), rootProjectClassLoaderScope.createChild("project-child2", null))
        child2State.mutableModel >> child2
        child2State.name >> "child2"
        projectState.childProjects >> ([child1State, child2State] as Set)
        [project, child1, childchild, child2].each {
            projectRegistry.addProject(it)
        }
    }

    private DefaultProject defaultProject(String name, ProjectState owner, ProjectInternal parent, File rootDir, ClassLoaderScope scope) {
        _ * owner.identityPath >> (parent == null ? Path.ROOT : parent.identityPath.child(name))
        _ * owner.projectPath >> (parent == null ? Path.ROOT : parent.projectPath.child(name))
        _ * owner.depth >> owner.projectPath.segmentCount()
        def project = TestUtil.instantiatorFactory().decorateLenient().newInstance(DefaultProject, name, parent, rootDir, new File(rootDir, 'build.gradle'), script, build, owner, projectServiceRegistryFactoryMock, scope, baseClassLoaderScope)
        _ * owner.applyToMutableState(_) >> { Consumer action -> action.accept(project) }
        return project
    }

    //TODO please move more coverage to NewDefaultProjectTest

    def scriptClasspath() {
        when:
        project.buildscript {
            repositories
        }

        then:
        1 * scriptHandlerMock.getRepositories()
    }

    def testProject() {
        expect:
        project == child1.parent
        project == child1.rootProject
        checkProject(project, null, 'root', rootDir)
    }

    private void checkProject(DefaultProject project, Project parent, String name, File projectDir) {
        assert project.parent.is(parent)
        assert project.name == name
        assert project.version == Project.DEFAULT_VERSION
        assert project.status == Project.DEFAULT_STATUS
        assert project.rootDir.is(rootDir)
        assert project.projectDir.is(projectDir)
        assert project.rootProject.is(this.project)
        assert project.buildFile == new File(projectDir, TEST_BUILD_FILE_NAME)
        assert project.projectEvaluator.is(projectEvaluator)
        assert project.antBuilderFactory.is(antBuilderFactoryMock)
        assert project.gradle.is(build)
        assert project.ant != null
        assert project.convention != null
        assert project.defaultTasks == []
        assert project.configurations.is(configurationContainerMock)
        assert project.repositories.is(repositoryHandlerMock)
        assert project.dependencyFactory.is(dependencyFactoryMock)
        assert !project.state.executed
        assert project.components.is(softwareComponentsMock)
    }

    def nullVersionAndStatus() {
        when:
        project.version = 'version'
        project.status = 'status'

        then:
        project.version == 'version'
        project.status == 'status'

        when:
        project.version = null
        project.status = null
        then:
        project.version == Project.DEFAULT_VERSION
        project.status == Project.DEFAULT_STATUS
    }

    def getGroup() {
        expect:
        project.group == ''
        childchild.group == 'root.child1'

        when:
        child1.group = ''
        then:
        child1.group == ''

        when:
        child1.group = null
        then:
        child1.group == 'root'
    }

    def executesActionBeforeEvaluation() {
        given:
        def listener = Mock(Action)
        project.beforeEvaluate(listener)

        when:
        project.projectEvaluationBroadcaster.beforeEvaluate(project)

        then:
        1 * listener.execute(project)
    }

    def executesActionAfterEvaluation() {
        given:
        def listener = Mock(Action)
        project.afterEvaluate(listener)

        when:
        project.projectEvaluationBroadcaster.afterEvaluate(project, null)

        then:
        1 * listener.execute(project)
    }

    def executesClosureBeforeEvaluation() {
        given:
        def listener = Mock(TestClosure)
        project.beforeEvaluate(TestUtil.toClosure(listener))

        when:
        project.projectEvaluationBroadcaster.beforeEvaluate(project)

        then:
        1 * listener.call(project)
    }

    def executesClosureAfterEvaluation() {
        given:
        def listener = Mock(TestClosure)
        project.afterEvaluate(TestUtil.toClosure(listener))

        when:
        project.projectEvaluationBroadcaster.afterEvaluate(project, null)

        then:
        1 * listener.call(project)
    }

    def evaluate() {
        when:
        def returnedProject = project.evaluate()

        then:
        1 * projectEvaluator.evaluate(project, project.state)
        returnedProject.is(project)
    }

    def evaluationDependsOn() {
        when:
        project.evaluationDependsOn(child1.path)

        then:
        1 * child1State.ensureConfigured()
    }

    def testEvaluationDependsOnChildren() {
        when:
        project.evaluationDependsOnChildren()

        then:
        1 * child1State.ensureConfigured()
        1 * child2State.ensureConfigured()
        0 * projectState.ensureConfigured()
        0 * chilchildState.ensureConfigured()
    }

    def evaluationDependsOnWithNullArgument() {
        when:
        project.evaluationDependsOn(null)

        then:
        thrown(InvalidUserDataException)
    }

    void evaluationDependsOnWithEmptyArgument() {
        when:
        project.evaluationDependsOn('')

        then:
        thrown(InvalidUserDataException)
    }

    def evaluationDependsOnWithCircularDependency() {
        when:
        project.evaluationDependsOn(child1.path)

        then:
        1 * child1State.ensureConfigured() >> {
            child1.evaluationDependsOn(project.path)
        }
    }

    def getChildProject() {
        expect:
        project.childProjectsUnchecked.size() == 2
        project.childProjectsUnchecked.child1.is(child1)
        project.childProjectsUnchecked.child2.is(child2)
    }

    def defaultTasks() {
        when:
        project.defaultTasks("a", "b")
        then:
        project.defaultTasks == ["a", "b"]
        when:
        project.defaultTasks("c")
        then:
        project.defaultTasks == ["c"]
    }

    def defaultTasksWithNull() {
        when:
        project.defaultTasks(null)
        then:
        thrown(InvalidUserDataException)
    }

    def defaultTasksWithSingleNullValue() {
        when:
        project.defaultTasks("a", null)
        then:
        thrown(InvalidUserDataException)
    }

    def canAccessTaskAsAProjectProperty() {
        expect:
        project.someTask.is(testTask)
    }

    def propertyShortCutForTaskCallWithNonexistentTask() {
        when:
        project.unknownTask
        then:
        thrown(MissingPropertyException)
    }

    def methodShortCutForTaskCallWithNonexistentTask() {
        when:
        project.unknownTask([dependsOn: '/task2'])
        then:
        thrown(groovy.lang.MissingMethodException)
    }

    private Set<Project> getListWithAllProjects() {
        [project, child1, child2, childchild]
    }

    private Set<Project> getListWithAllChildProjects() {
        [child1, child2, childchild]
    }

    def getPath() {
        expect:
        child1.path == Project.PATH_SEPARATOR + "child1"
        project.path == Project.PATH_SEPARATOR
    }

    def getProject() {
        expect:
        project.project(Project.PATH_SEPARATOR).is(project)
        assertLifecycleAwareProjectOf(project.project(Project.PATH_SEPARATOR + "child1"), child1)
        assertLifecycleAwareProjectOf(project.project("child1"), child1)
        assertLifecycleAwareProjectOf(child1.project("childchild"), childchild)
        assertLifecycleAwareProjectOf(childchild.project(Project.PATH_SEPARATOR + "child1"), child1)
    }

    def getProjectWithUnknownAbsolutePath() {
        when:
        project.project(Project.PATH_SEPARATOR + "unknownchild")
        then:
        def e = thrown(UnknownProjectException)
        e.message == "Project with path ':unknownchild' could not be found in root project 'root'."
    }

    def getProjectWithUnknownRelativePath() {
        when:
        project.project("unknownchild")
        then:
        def e = thrown(UnknownProjectException)
        e.message == "Project with path 'unknownchild' could not be found in root project 'root'."
    }

    def getProjectWithEmptyPath() {
        when:
        project.project("")
        then:
        thrown(InvalidUserDataException)
    }

    def getProjectWithNullPath() {
        when:
        project.project(null)
        then:
        thrown(InvalidUserDataException)
    }

    def findProject() {
        expect:
        project.findProject(Project.PATH_SEPARATOR).is(project)
        assertLifecycleAwareProjectOf(project.findProject(Project.PATH_SEPARATOR + "child1"), child1)
        assertLifecycleAwareProjectOf(project.findProject("child1"), child1)
        assertLifecycleAwareProjectOf(child1.findProject('childchild'), childchild)
        assertLifecycleAwareProjectOf(childchild.findProject(Project.PATH_SEPARATOR + "child1"), child1)
    }

    def findProjectWithUnknownAbsolutePath() {
        expect:
        project.findProject(Project.PATH_SEPARATOR + "unknownchild") == null
    }

    def findProjectWithUnknownRelativePath() {
        expect:
        project.findProject("unknownChild") == null
    }

    def testGetProjectWithClosure() {
        given:
        String newPropValue = 'someValue'

        when:
        def child = project.project("child1") {
            ext.newProp = newPropValue
        }

        then:
        assertLifecycleAwareProjectOf(child, child1)
        child1.newProp == newPropValue
    }

    def getProjectWithAction() {
        given:
        def child1 = project.project("child1")
        def action = Mock(Action)

        when:
        def child = project.project("child1", action)

        then:
        1 * action.execute(child1)
        0 * action._
        assertLifecycleAwareProjectOf(child, child1)
    }

    def methodMissing() {
        given:
        boolean closureCalled = false
        Closure testConfigureClosure = { closureCalled = true }
        when:
        project.someTask(testConfigureClosure)
        then:
        closureCalled

        when:
        project.convention.plugins.test = new TestConvention()
        then:
        project.scriptMethod(testConfigureClosure) == TestConvention.METHOD_RESULT

        when:
        project.script = createScriptForMethodMissingTest('projectScript')
        then:
        project.scriptMethod(testConfigureClosure) == 'projectScript'
    }

    private groovy.lang.Script createScriptForMethodMissingTest(String returnValue) {
        String code = """
def scriptMethod(Closure closure) {
    "$returnValue"
}
"""
        TestUtil.createScript(code)
    }

    def setPropertyAndPropertyMissingWithProjectProperty() {
        given:
        String propertyName = 'propName'
        String expectedValue = 'somevalue'

        when:
        project.ext."$propertyName" = expectedValue

        then:
        project."$propertyName" == expectedValue
        child1."$propertyName" == expectedValue
    }

    def propertyMissingWithExistingConventionProperty() {
        given:
        String propertyName = 'conv'
        String expectedValue = 'somevalue'

        when:
        project.convention.plugins.test = new TestConvention()
        project.convention.conv = expectedValue

        then:
        project."$propertyName" == expectedValue
        project.convention."$propertyName" == expectedValue
        child1."$propertyName" == expectedValue
    }

    def setPropertyAndPropertyMissingWithConventionProperty() {
        given:
        String expectedValue = 'somevalue'

        when:
        project.convention.plugins.test = new TestConvention()
        project.conv = expectedValue

        then:
        project.conv == expectedValue
        project.convention.plugins.test.conv == expectedValue
        child1.conv == expectedValue
    }

    def setPropertyAndPropertyMissingWithProjectAndConventionProperty() {
        given:
        String propertyName = 'archivesBaseName'
        String expectedValue = 'somename'

        when:
        project.ext.archivesBaseName = expectedValue
        project.convention.plugins.test = new TestConvention()
        project.convention.archivesBaseName = 'someothername'
        project."$propertyName" = expectedValue

        then:
        project."$propertyName" == expectedValue
        project.convention."$propertyName" == 'someothername'
    }

    def propertyMissingWithNullProperty() {
        when:
        project.ext.nullProp = null
        then:
        project.nullProp == null
        project.hasProperty('nullProp')
    }

    def findProperty() {
        when:
        project.ext.someProp = "somePropValue"
        then:
        project.findProperty('someProp') == "somePropValue"
        project.findProperty("someNonexistentProp") == null
    }

    def setPropertyNullValue() {
        when:
        project.ext.someProp = "somePropValue"
        project.setProperty("someProp", null)
        then:
        project.hasProperty("someProp")
        project.findProperty("someProp") == null
        project.someProp == null
    }

    def propertyMissingWithUnknownProperty() {
        when:
        project.unknownProperty
        then:
        thrown(MissingPropertyException)
    }

    def hasProperty() {
        given:
        String propertyName = 'beginIndex'

        expect:
        project.hasProperty('name')
        !project.hasProperty(propertyName)
        !child1.hasProperty(propertyName)

        when:
        project.convention.plugins.test = new FieldPosition(0)
        project."$propertyName" = 5
        then:
        project.hasProperty(propertyName)
        child1.hasProperty(propertyName)
    }

    def properties() {
        given:
        serviceRegistryMock.get(ServiceRegistryFactory) >> Stub(ServiceRegistryFactory)
        serviceRegistryMock.get(ProjectBuildProvider) >> Stub(ProjectBuildProvider)

        when:
        project.ext.additional = 'additional'

        then:
        def properties = project.properties
        properties.name == 'root'
        properties.additional == 'additional'
        properties['someTask'] == testTask
    }

    def extraPropertiesAreInheritable() {
        when:
        project.ext.somename = 'somevalue'
        then:
        project.inheritedScope.hasProperty('somename')
        project.inheritedScope.getProperty('somename') == 'somevalue'
    }

    def conventionPropertiesAreInheritable() {
        when:
        project.convention.plugins.test = new TestConvention()
        project.convention.plugins.test.conv = 'somevalue'
        then:
        project.inheritedScope.hasProperty('conv')
        project.inheritedScope.getProperty('conv') == 'somevalue'
    }

    def inheritedPropertiesAreInheritable() {
        when:
        project.ext.somename = 'somevalue'
        then:
        child1.inheritedScope.hasProperty('somename')
        child1.inheritedScope.getProperty('somename') == 'somevalue'
    }

    def getProjectProperty() {
        expect:
        project.is(project.getProject())
    }

    def allProjectsField() {
        expect:
        project.allprojects == getListWithAllProjects()
    }

    def children() {
        expect:
        project.subprojects == getListWithAllChildProjects()
    }

    def buildDir() {
        expect:
        project.buildDir == new File(rootDir, "build")

        when:
        project.buildDir = "abc"
        then:
        child1.buildDir == new File(rootDir, "abc")
    }

    def cachingOfAnt() {
        expect:
        project.ant.is(testAntBuilder)
        project.ant.is(project.ant)
    }

    def ant() {
        given:
        Closure configureClosure = { fileset(dir: 'dir', id: 'fileset') }
        when:
        project.ant(configureClosure)
        then:
        project.ant.project.getReference('fileset') instanceof FileSet
    }

    def createAntBuilder() {
        expect:
        project.createAntBuilder().is(testAntBuilder)
    }

    def compareTo() {
        expect:
        project < child1
        child1 < child2
        child1 < childchild
        child2 < childchild
    }

    def depthCompare() {
        expect:
        project.depthCompare(child1) < 0
        child1.depthCompare(project) > 0
        child1.depthCompare(child2) == 0
    }

    def depth() {
        expect:
        project.depth == 0
        child1.depth == 1
        child2.depth == 1
        childchild.depth == 2
    }

    def subprojects() {
        expect:
        checkConfigureProject('subprojects', listWithAllChildProjects)
    }

    def allprojects() {
        expect:
        checkConfigureProject('allprojects', listWithAllProjects)
    }

    def configureProjects() {
        expect:
        checkConfigureProject('configure', [project, child1] as Set)
    }

    private void checkConfigureProject(String configureMethod, Set projectsToCheck) {
        String propValue = 'someValue'
        if (configureMethod == 'configure') {
            project."$configureMethod" projectsToCheck as List,
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
            assert it.testSubProp == propValue
        }
    }

    static class Bean {
        String value
    }

    def configure() {
        when:
        def actualBean = project.configure(new Bean()) {
            value = 'value'
        }
        then:
        actualBean.value == 'value'
    }

    def setName() {
        when:
        project.name = "someNewName"
        then:
        def e = thrown(GroovyRuntimeException)
        e.message == "Cannot set the value of read-only property 'name' for root project 'root' of type ${Project.name}."
    }

    def convertsAbsolutePathToAbsolutePath() {
        expect:
        project.absoluteProjectPath(':') == ':'
        project.absoluteProjectPath(':other') == ':other'
        child1.absoluteProjectPath(':') == ':'
        child1.absoluteProjectPath(':other') == ':other'
    }

    def convertsRelativePathToAbsolutePath() {
        expect:
        project.absoluteProjectPath('task') == ':task'
        project.absoluteProjectPath('sub:other') == ':sub:other'
        child1.absoluteProjectPath('task') == ':child1:task'
        child1.absoluteProjectPath('sub:other') == ':child1:sub:other'
    }

    def convertsRelativePathToRelativePath() {
        expect:
        project.relativeProjectPath('task') == 'task'
        project.relativeProjectPath('sub:other') == 'sub:other'
    }

    def convertsAbsolutePathToRelativePath() {
        expect:
        project.relativeProjectPath(':') == ':'
        project.relativeProjectPath(':task') == 'task'
        project.relativeProjectPath(':sub:other') == 'sub:other'
        child1.relativeProjectPath(':child1') == ':child1'
        child1.relativeProjectPath(':child1:task') == 'task'
        child1.relativeProjectPath(':child12:task') == ':child12:task'
        child1.relativeProjectPath(':sub:other') == ':sub:other'
    }

    def createsADomainObjectContainer() {
        expect:
        project.container(String) instanceof FactoryNamedDomainObjectContainer
        project.container(String, Stub(NamedDomainObjectFactory)) instanceof FactoryNamedDomainObjectContainer
        project.container(String, {}) instanceof FactoryNamedDomainObjectContainer
    }

    def selfAccessWithoutLifecycleAwareWrapping() {
        expect:
        project.project(":child1").parent instanceof DefaultProject
        project.project(":child1").rootProject instanceof DefaultProject
        child1.allprojects { project ->
            if (project.name == "child1") {
                assert project instanceof DefaultProject
            } else {
                assert project instanceof LifecycleAwareProject
            }
        }
        child1.getAllprojects().forEach { project ->
            if (project.name == "child1") {
                assert project instanceof DefaultProject
            } else {
                assert project instanceof LifecycleAwareProject
            }
        }
    }

    def referrerIsPreserved() {
        expect:
        assertLifecycleAwareWithReferrer(child1) { rootProject }
        assertLifecycleAwareWithReferrer(child1) { parent }
        assertLifecycleAwareWithReferrer(child1) { it.project.parent }
        assertLifecycleAwareWithReferrer(project) { childProjects.values().first() }
        assertLifecycleAwareWithReferrer(project) { getAllprojects()[1] }
        assertLifecycleAwareWithReferrer(project) { getSubprojects()[0] }
        assertLifecycleAwareWithReferrer(project) { findProject(":child1") }
        assertLifecycleAwareWithReferrer(project) { project(":child1") }
        assertLifecycleAwareWithReferrer(project) { project(":child1") {} }
        assertLifecycleAwareWithReferrer(project) { project(":child1", Actions.doNothing()) }

        def p = project
        p.allprojects { if (it != p) { assertLifecycleAwareWithReferrer(it, p) } }
        p.subprojects { assertLifecycleAwareWithReferrer(it, p) }
    }

    static boolean assertLifecycleAwareWithReferrer(Project referrer, @DelegatesTo(Project.class) Closure navigate) {
        navigate.delegate = referrer
        assertLifecycleAwareWithReferrer(navigate(referrer), referrer)
    }

    static boolean assertLifecycleAwareWithReferrer(Project project, Project referrer) {
        project instanceof LifecycleAwareProject && project.referrer == referrer
    }

    static boolean assertLifecycleAwareProjectOf(Project crosslyAccessed, Project of) {
        crosslyAccessed instanceof LifecycleAwareProject && crosslyAccessed == of
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

