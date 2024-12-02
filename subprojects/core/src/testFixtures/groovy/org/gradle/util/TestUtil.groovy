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
package org.gradle.util

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.MutationGuard
import org.gradle.api.internal.MutationGuards
import org.gradle.api.internal.collections.DefaultDomainObjectCollectionFactory
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.internal.file.DefaultFilePropertyFactory
import org.gradle.api.internal.file.DefaultProjectLayout
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.model.DefaultObjectFactory
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.TaskInstantiator
import org.gradle.api.internal.provider.DefaultPropertyFactory
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.properties.annotations.OutputPropertyRoleAnnotationHandler
import org.gradle.api.model.ObjectFactory
import org.gradle.api.problems.ProblemReporter
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory
import org.gradle.api.problems.internal.DefaultProblems
import org.gradle.api.problems.internal.ExceptionProblemRegistry
import org.gradle.api.problems.internal.InternalProblemReporter
import org.gradle.api.problems.internal.InternalProblems
import org.gradle.api.problems.internal.Problem
import org.gradle.api.problems.internal.ProblemSummarizer
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.util.internal.PatternSets
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.instantiation.InjectAnnotationHandler
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.instantiation.generator.DefaultInstantiatorFactory
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.InMemoryCacheFactory
import org.gradle.internal.model.StateTransitionControllerFactory
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.state.ManagedFactoryRegistry
import org.gradle.internal.work.DefaultWorkerLimits
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.work.TestWorkerLeaseService
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.testfixtures.internal.ProjectBuilderImpl
import org.spockframework.lang.Wildcard

import javax.annotation.Nullable
import java.util.function.Supplier

class TestUtil {
    public static final Closure TEST_CLOSURE = {}
    private static InstantiatorFactory instantiatorFactory
    private static ManagedFactoryRegistry managedFactoryRegistry
    private static ServiceRegistry services

    private final File rootDir
    private final File userHomeDir

    private TestUtil(File rootDir) {
        this(rootDir, new File(rootDir, "userHome"))
    }

    private TestUtil(File rootDir, File userHomeDir) {
        NativeServicesTestFixture.initialize()
        this.rootDir = rootDir
        this.userHomeDir = userHomeDir
    }

    static InstantiatorFactory instantiatorFactory() {
        if (instantiatorFactory == null) {
            instantiatorFactory = createInstantiatorFactory({ [] })
        }
        return instantiatorFactory
    }

    static InstantiatorFactory createInstantiatorFactory(Supplier<List<InjectAnnotationHandler>> injectHandlers) {
        NativeServicesTestFixture.initialize()
        return new DefaultInstantiatorFactory(new TestCrossBuildInMemoryCacheFactory(), injectHandlers.get(), new OutputPropertyRoleAnnotationHandler([]))
    }

    static ManagedFactoryRegistry managedFactoryRegistry() {
        if (managedFactoryRegistry == null) {
            NativeServicesTestFixture.initialize()
            managedFactoryRegistry = ProjectBuilderImpl.getGlobalServices().get(ManagedFactoryRegistry.class)
        }
        return managedFactoryRegistry
    }

    static DomainObjectCollectionFactory domainObjectCollectionFactory() {
        return services().get(DomainObjectCollectionFactory)
    }

    static ProviderFactory providerFactory() {
        return services().get(ProviderFactory)
    }

    static TaskDependencyFactory taskDependencyFactory() {
        return services().get(TaskDependencyFactory)
    }

    static PropertyFactory propertyFactory() {
        return services().get(PropertyFactory)
    }

    static <T> T newInstance(Class<T> clazz, Object... params) {
        return objectFactory().newInstance(clazz, params)
    }

    static ObjectFactory objectFactory() {
        return services().get(ObjectFactory)
    }

    static TestProblems problemsService() {
        return services().get(TestProblems)
    }

    static ObjectFactory objectFactory(TestFile baseDir) {
        def fileResolver = TestFiles.resolver(baseDir)
        def fileCollectionFactory = TestFiles.fileCollectionFactory(baseDir)
        return createServices(fileResolver, fileCollectionFactory).get(ObjectFactory)
    }

    static CalculatedValueContainerFactory calculatedValueContainerFactory() {
        return new CalculatedValueContainerFactory(new TestWorkerLeaseService(), services())
    }

    static InMemoryCacheFactory inMemoryCacheFactory() {
        return new InMemoryCacheFactory(new DefaultWorkerLimits(Runtime.getRuntime().availableProcessors()), calculatedValueContainerFactory())
    }

    static StateTransitionControllerFactory stateTransitionControllerFactory() {
        return new StateTransitionControllerFactory(new TestWorkerLeaseService())
    }

    private static ServiceRegistry createServices(FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, Action<ServiceRegistration> registrations = {}) {
        def services = new DefaultServiceRegistry()
        services.register {
            registrations.execute(it)
            it.add(ProviderFactory, new TestProviderFactory())
            it.add(TestCrossBuildInMemoryCacheFactory)
            it.add(NamedObjectInstantiator)
            it.add(CollectionCallbackActionDecorator, CollectionCallbackActionDecorator.NOOP)
            it.add(MutationGuard, MutationGuards.identity())
            it.add(DefaultDomainObjectCollectionFactory)
            it.add(PropertyHost, PropertyHost.NO_OP)
            it.add(TaskDependencyFactory, DefaultTaskDependencyFactory.withNoAssociatedProject())
            it.add(DocumentationRegistry, new DocumentationRegistry())
            it.add(FileCollectionFactory, fileCollectionFactory)
            it.add(DefaultPropertyFactory)
            it.addProvider(new ServiceRegistrationProvider() {
                @Provides
                InstantiatorFactory createInstantiatorFactory() {
                    TestUtil.instantiatorFactory()
                }

                @Provides
                ObjectFactory createObjectFactory(InstantiatorFactory instantiatorFactory, NamedObjectInstantiator namedObjectInstantiator, DomainObjectCollectionFactory domainObjectCollectionFactory, TaskDependencyFactory taskDependencyFactory, PropertyFactory propertyFactory) {
                    def filePropertyFactory = new DefaultFilePropertyFactory(PropertyHost.NO_OP, fileResolver, fileCollectionFactory)
                    return new DefaultObjectFactory(instantiatorFactory.decorate(services), namedObjectInstantiator, TestFiles.directoryFileTreeFactory(), TestFiles.patternSetFactory, propertyFactory, filePropertyFactory, taskDependencyFactory, fileCollectionFactory, domainObjectCollectionFactory)
                }

                @Provides
                ProjectLayout createProjectLayout() {
                    def filePropertyFactory = new DefaultFilePropertyFactory(PropertyHost.NO_OP, fileResolver, fileCollectionFactory)
                    return new DefaultProjectLayout(fileResolver.resolve("."), fileResolver, DefaultTaskDependencyFactory.withNoAssociatedProject(), PatternSets.getNonCachingPatternSetFactory(), PropertyHost.NO_OP, fileCollectionFactory, filePropertyFactory, filePropertyFactory)
                }

                @Provides
                TestProblems createProblemsService() {
                    new TestProblems()
                }

                @Provides
                ChecksumService createChecksumService() {
                    new ChecksumService() {
                        @Override
                        HashCode md5(File file) {
                            Hashing.md5().hashBytes(file.bytes)
                        }

                        @Override
                        HashCode sha1(File file) {
                            Hashing.sha1().hashBytes(file.bytes)
                        }

                        @Override
                        HashCode sha256(File file) {
                            Hashing.sha256().hashBytes(file.bytes)
                        }

                        @Override
                        HashCode sha512(File file) {
                            Hashing.sha512().hashBytes(file.bytes)
                        }

                        @Override
                        HashCode hash(File src, String algorithm) {
                            def algo = algorithm.toLowerCase().replaceAll('-', '')
                            Hashing."$algo"().hashBytes(src.bytes)
                        }
                    }
                }
            })
        }
        return services
    }

    static ServiceRegistry services() {
        if (services == null) {
            services = createTestServices()
        }
        return services
    }

    static ServiceRegistry createTestServices(Action<ServiceRegistration> registrations = {}) {
        createServices(TestFiles.resolver().newResolver(new File(".").absoluteFile), TestFiles.fileCollectionFactory(), registrations)
    }

    static NamedObjectInstantiator objectInstantiator() {
        return services().get(NamedObjectInstantiator)
    }

    static FeaturePreviews featurePreviews() {
        return new FeaturePreviews()
    }

    static TestUtil create(File rootDir, File userHomeDir = null) {
        return new TestUtil(rootDir, userHomeDir)
    }

    static TestUtil create(TestDirectoryProvider testDirectoryProvider) {
        return new TestUtil(testDirectoryProvider.testDirectory)
    }

    <T extends Task> T task(Class<T> type) {
        return createTask(type, createRootProject(this.rootDir, this.userHomeDir))
    }

    static <T extends Task> T createTask(Class<T> type, ProjectInternal project) {
        return createTask(type, project, 'name')
    }

    static <T extends Task> T createTask(Class<T> type, ProjectInternal project, String name) {
        return project.services.get(TaskInstantiator).create(name, type)
    }

    static ProjectBuilder builder(File rootDir) {
        return ProjectBuilder.builder().withProjectDir(rootDir)
    }

    static ProjectBuilder builder(TestDirectoryProvider temporaryFolder) {
        return builder(temporaryFolder.testDirectory)
    }

    ProjectInternal rootProject() {
        createRootProject(rootDir, userHomeDir)
    }

    static ProjectInternal createRootProject(File rootDir, File userHomeDir = null) {
        def builder = ProjectBuilder
            .builder()
            .withProjectDir(rootDir)
            .withName("test-project")
        if (userHomeDir != null) {
            builder.withGradleUserHomeDir(userHomeDir)
        }
        return builder.build()
    }

    static ProjectInternal createChildProject(ProjectInternal parent, String name, File projectDir = null) {
        return ProjectBuilder
            .builder()
            .withName(name)
            .withParent(parent)
            .withProjectDir(projectDir)
            .build()
    }

    static groovy.lang.Script createScript(String code) {
        new GroovyShell().parse(code)
    }

    static Object call(String text, Object... params) {
        toClosure(text).call(*params)
    }

    static Closure toClosure(String text) {
        return new GroovyShell().evaluate("return " + text)
    }

    static Closure toClosure(TestClosure closure) {
        return { param -> closure.call(param) }
    }

    static Closure returns(Object value) {
        return { value }
    }

    static ChecksumService getChecksumService() {
        services().get(ChecksumService)
    }

    static Throwable getRootCause(Throwable t) {
        if (t == null) {
            return null
        }

        def cause = t
        while (true) {
            def nextCause = cause.cause
            if (nextCause == null || nextCause === cause) {
                break
            }
            cause = nextCause
        }

        return cause
    }

    static <T extends Throwable> boolean isOrIsCausedBy(Throwable t, Class<T> expectedCauseType) {
        def cause = t
        while (cause != null) {
            if (expectedCauseType.isInstance(cause)) {
                return true
            }
            cause = cause.cause
        }
        return false
    }
}

interface TestClosure {
    Object call(Object param);
}

class TestProblems implements InternalProblems {
    private final TestProblemSummarizer summarizer
    private final InternalProblems delegate

    TestProblems() {
        this.summarizer = new TestProblemSummarizer()
        this.delegate = new DefaultProblems(
            summarizer,
            null,
            new TestCurrentBuildOperationRef(),
            new ExceptionProblemRegistry(),
            null
        )
    }

    @Override
    ProblemReporter getReporter() {
        delegate.reporter
    }

    @Override
    InternalProblemReporter getInternalReporter() {
        delegate.internalReporter
    }

    @Override
    AdditionalDataBuilderFactory getAdditionalDataBuilderFactory() {
        delegate.additionalDataBuilderFactory
    }

    void assertProblemEmittedOnce(Object expectedProblem) {
        assert summarizer.emitted.size() == 1
        def actualProblem = summarizer.emitted[0]
        if (expectedProblem instanceof Closure) {
            assert expectedProblem.call(actualProblem)
        } else if (expectedProblem instanceof Problem) {
            assert expectedProblem == actualProblem
        } else {
            assert expectedProblem instanceof Wildcard
        }
    }

    void recordEmittedProblems() {
        summarizer.reset()
    }

    void resetRecordedProblems() {
        summarizer.reset()
    }
}

class TestProblemSummarizer implements ProblemSummarizer {
    List emitted = []

    @Override
    void emit(Problem problem, @Nullable OperationIdentifier id) {
        emitted.add(problem)
    }

    void reset() {
        emitted.clear()
    }

    @Override
    String getId() {
        //no op
        return ""
    }

    @Override
    void report(File reportDir, ProblemConsumer validationFailures) {
        //no op
    }
}

class TestCurrentBuildOperationRef extends CurrentBuildOperationRef {
    @Override
    OperationIdentifier getId() {
        new OperationIdentifier(42)
    }
}
