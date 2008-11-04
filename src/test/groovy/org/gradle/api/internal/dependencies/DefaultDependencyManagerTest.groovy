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

package org.gradle.api.internal.dependencies

import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.DualResolver
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.gradle.api.DependencyManager
import org.gradle.api.InvalidUserDataException
import org.gradle.api.dependencies.Dependency
import org.gradle.api.dependencies.ExcludeRuleContainer
import org.gradle.api.dependencies.PublishArtifact
import org.gradle.api.dependencies.ResolverContainer
import org.gradle.api.dependencies.UnknownConfigurationException
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.util.WrapUtil.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.dependencies.maven.MavenPom
import org.gradle.api.dependencies.Configuration
import org.gradle.util.HelperUtil
import org.gradle.api.dependencies.PublishArtifact
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.gradle.api.internal.Transformer
import org.apache.ivy.core.module.id.ModuleId

/**
 * @author Hans Dockter
 */
@RunWith (org.jmock.integration.junit4.JMock)
public class DefaultDependencyManagerTest extends AbstractDependencyContainerTest {
    static final String TEST_CONFIG = 'testConfig';

    DefaultDependencyManager dependencyManager
    DefaultSettingsConverter settingsConverter
    DefaultModuleDescriptorConverter moduleDescriptorConverter
    IDependencyResolver dependencyResolverMock
    IDependencyPublisher dependencyPublisherMock
    IIvyFactory ivyFactoryMock
    File buildResolverDir
    ResolverFactory resolverFactoryMock
    BuildResolverHandler mockSpecialResolverHandler
    RepositoryResolver expectedBuildResolver

    List expectedClasspath
    ModuleDescriptor expectedModuleDescriptor
    IvySettings expectedSettings
    Ivy expectedIvy

    ExcludeRuleContainer testExcludeRuleContainer;

    JUnit4GroovyMockery context = new JUnit4GroovyMockery();

    public DefaultDependencyContainer getTestObj() {
        return dependencyManager
    }

    @Before public void setUp() {
        super.setUp()
        context.setImposteriser(ClassImposteriser.INSTANCE)
        expectedClasspath = ['a']
        expectedModuleDescriptor = {} as ModuleDescriptor
        expectedSettings = new IvySettings();
        expectedIvy = new Ivy();
        ivyFactoryMock = context.mock(IIvyFactory)
        resolverFactoryMock = context.mock(ResolverFactory)
        dependencyResolverMock = context.mock(IDependencyResolver)
        dependencyPublisherMock = context.mock(IDependencyPublisher)
        testExcludeRuleContainer = new DefaultExcludeRuleContainer()
        settingsConverter = context.mock(DefaultSettingsConverter)
        buildResolverDir = new File('buildResolverDir')
        moduleDescriptorConverter = context.mock(DefaultModuleDescriptorConverter)
        dependencyManager = new DefaultDependencyManager(ivyFactoryMock, dependencyFactory, resolverFactoryMock, settingsConverter,
                moduleDescriptorConverter, dependencyResolverMock, dependencyPublisherMock, buildResolverDir, testExcludeRuleContainer)
        dependencyManager.project = project
        dependencyManager.clientModuleRegistry = [a: 'b']
        dependencyManager.defaultConfs = testDefaultConfs
        expectedBuildResolver = new FileSystemResolver()
        mockSpecialResolverHandler = context.mock(BuildResolverHandler)
        dependencyManager.buildResolverHandler = mockSpecialResolverHandler
        context.checking {
            allowing(mockSpecialResolverHandler).getBuildResolverDir(); will(returnValue(buildResolverDir))
            allowing(mockSpecialResolverHandler).getBuildResolver(); will(returnValue(expectedBuildResolver))
        }
    }

    @Test public void testInit() {
        assert dependencyManager.ivyFactory.is(ivyFactoryMock)
        assert dependencyManager.resolverFactory.is(resolverFactoryMock)
        assert dependencyManager.settingsConverter.is(settingsConverter)
        assert dependencyManager.moduleDescriptorConverter.is(moduleDescriptorConverter)
        assert dependencyManager.dependencyResolver.is(dependencyResolverMock)
        assert dependencyManager.dependencyPublisher.is(dependencyPublisherMock)
        assert dependencyManager.excludeRules.is(testExcludeRuleContainer)
        assert dependencyManager.buildResolverDir.is(buildResolverDir)
        assert dependencyManager.classpathResolvers
        assert dependencyManager.failForMissingDependencies
        assert dependencyManager.localReposCacheHandler.buildResolverDir.is(buildResolverDir)
        assert dependencyManager.buildResolverHandler.buildResolverDir.is(buildResolverDir)
        assertNotNull(dependencyManager.defaultMavenScopeMapping)
        assertEquals([], dependencyManager.getAbsoluteArtifactPatterns())
        assertEquals([] as Set, dependencyManager.getArtifactParentDirs())
        assertEquals(DependencyManager.DEFAULT_ARTIFACT_PATTERN, dependencyManager.defaultArtifactPattern)
    }

    @Test public void testAddArtifacts() {
        List gradleArtifacts = [[:] as PublishArtifact, [:] as PublishArtifact, [:] as PublishArtifact, [:] as PublishArtifact]
        testObj.addArtifacts(AbstractDependencyContainerTest.TEST_CONFIGURATION, gradleArtifacts[0], gradleArtifacts[1])
        assertEquals([(AbstractDependencyContainerTest.TEST_CONFIGURATION): [gradleArtifacts[0], gradleArtifacts[1]]], testObj.artifacts)
        testObj.addArtifacts(AbstractDependencyContainerTest.TEST_CONFIGURATION, gradleArtifacts[2], gradleArtifacts[3])
        assertEquals([(AbstractDependencyContainerTest.TEST_CONFIGURATION): [gradleArtifacts[0], gradleArtifacts[1], gradleArtifacts[2], gradleArtifacts[3]]], testObj.artifacts)
    }

    @Test public void testResolveTask() {
        String testConf2 = "testConf2"
        dependencyManager.addConfiguration(testConf2)
        String testTaskName = 'myTask'
        dependencyManager.linkConfWithTask(TEST_CONFIG, testTaskName)
        dependencyManager.linkConfWithTask(testConf2, testTaskName)
        List expectedClasspath2 = ['b']
        prepareMocks();
        context.checking {
            one(dependencyResolverMock).resolve(testConf2, expectedIvy, expectedModuleDescriptor,
                    dependencyManager.failForMissingDependencies);
            will(returnValue(expectedClasspath2))
        }
        assertEquals((expectedClasspath + expectedClasspath2) as Set, dependencyManager.resolveTask(testTaskName) as Set)
    }

    @Test (expected = InvalidUserDataException) public void testResolveTaskwithUnmappedTasked() {
        dependencyManager.resolveTask('unmappedTask')
    }

    @Test public void testResolve() {
        prepareMocks();
        assertSame(expectedClasspath, dependencyManager.resolve(TEST_CONFIG))
    }

    @Test public void testResolveWithArgs() {
        prepareMocks(false);
        context.checking {
            allowing(moduleDescriptorConverter).convert(dependencyManager, false); will(returnValue(expectedModuleDescriptor))
        }
        assertSame(expectedClasspath, dependencyManager.resolve(TEST_CONFIG, false, false))
    }

    @Test public void testAntpath() {
        expectedClasspath = ['a', 'b']
        prepareMocks();
        assertEquals(expectedClasspath.join(':'), dependencyManager.antpath(TEST_CONFIG))
    }

    private void prepareMocks() {
        prepareMocks(dependencyManager.failForMissingDependencies)
        context.checking {
            allowing(moduleDescriptorConverter).convert(dependencyManager, true); will(returnValue(expectedModuleDescriptor))
        }
    }

    private void prepareMocks(boolean failForMissingDependencies) {
        context.checking {
            allowing(settingsConverter).convert(
                    dependencyManager.classpathResolvers.resolverList,
                    [],
                    new File(project.getGradleUserHome()),
                    expectedBuildResolver,
                    dependencyManager.getClientModuleRegistry());
            will(returnValue(expectedSettings))
            allowing(ivyFactoryMock).createIvy(expectedSettings); will(returnValue(expectedIvy))
            one(dependencyResolverMock).resolve(TEST_CONFIG, expectedIvy, expectedModuleDescriptor,
                    failForMissingDependencies);
            will(returnValue(expectedClasspath))
        }
    }

    @Test public void testPublish() {
        List expectedConfigs = [TEST_CONFIG]
        ResolverContainer expectedResolvers = new ResolverContainer(null);
        boolean expectedUploadModuleDescriptor = false
        context.checking {
            allowing(settingsConverter).convert(
                    dependencyManager.classpathResolvers.resolverList,
                    [],
                    new File(project.getGradleUserHome()),
                    expectedBuildResolver,
                    dependencyManager.getClientModuleRegistry());
            will(returnValue(expectedSettings))
            allowing(ivyFactoryMock).createIvy(expectedSettings); will(returnValue(expectedIvy))
            allowing(moduleDescriptorConverter).convert(dependencyManager, true); will(returnValue(expectedModuleDescriptor))
            one(dependencyPublisherMock).publish(expectedConfigs, expectedResolvers, expectedModuleDescriptor,
                    expectedUploadModuleDescriptor, project.getBuildDir(), dependencyManager, expectedIvy.getPublishEngine());
        }
        dependencyManager.publish(expectedConfigs, expectedResolvers, expectedUploadModuleDescriptor)
    }

    @Test public void linkConfWithTask() {
        String testConf2 = 'testConf2'
        String testTaskName1 = 'task1'
        String testTaskName2 = 'task2'
        dependencyManager.addConfiguration(testConf2)
        dependencyManager.linkConfWithTask(TEST_CONFIG, testTaskName1)
        dependencyManager.linkConfWithTask(TEST_CONFIG, testTaskName2)
        dependencyManager.linkConfWithTask(testConf2, testTaskName1)
        assertEquals([TEST_CONFIG, testConf2] as Set, dependencyManager.confs4Task[testTaskName1])
        assertEquals([TEST_CONFIG] as Set, dependencyManager.confs4Task[testTaskName2])
        assertEquals([testTaskName1, testTaskName2] as Set, dependencyManager.tasks4Conf[TEST_CONFIG])
        assertEquals([testTaskName1] as Set, dependencyManager.tasks4Conf[testConf2])
    }

    @Test public void unlinkConfWithTask() {
        String testConf2 = 'testConf2'
        String testTaskName1 = 'task1'
        String testTaskName2 = 'task2'
        dependencyManager.addConfiguration(testConf2)
        dependencyManager.linkConfWithTask(TEST_CONFIG, testTaskName1)
        dependencyManager.linkConfWithTask(TEST_CONFIG, testTaskName2)
        dependencyManager.linkConfWithTask(testConf2, testTaskName1)

        dependencyManager.unlinkConfWithTask(TEST_CONFIG, testTaskName2)
        dependencyManager.unlinkConfWithTask(testConf2, testTaskName1)
        assertEquals([testTaskName1] as Set, dependencyManager.tasks4Conf[TEST_CONFIG])
        assertEquals([] as Set, dependencyManager.tasks4Conf[testConf2])
    }

    @Test (expected = InvalidUserDataException)
    public void unlinkConfWithTaskWithUnlinkedTask() {
        String testTaskName1 = 'task1'
        String testTaskName2 = 'task2'
        dependencyManager.linkConfWithTask(TEST_CONFIG, testTaskName1)
        dependencyManager.unlinkConfWithTask(TEST_CONFIG, testTaskName2)
    }

    @Test (expected = InvalidUserDataException)
    public void unlinkConfWithTaskWithUnlinkedConf() {
        String testTaskName1 = 'task1'
        dependencyManager.linkConfWithTask(TEST_CONFIG, testTaskName1)
        dependencyManager.unlinkConfWithTask('unlinkedConf', testTaskName1)
    }

    @Test (expected = InvalidUserDataException) public void testlinkConfWithTaskWithNullConf() {
        dependencyManager.linkConfWithTask(null, 'sometask')
    }

    @Test (expected = InvalidUserDataException) public void testlinkConfWithTaskWithNullTask() {
        dependencyManager.linkConfWithTask(TEST_CONFIG, null)
    }

    @Test (expected = InvalidUserDataException) public void testlinkConfWithTaskWithEmptyConf() {
        dependencyManager.linkConfWithTask('', 'sometask')
    }

    @Test (expected = InvalidUserDataException) public void testlinkConfWithTaskWithEmptyTask() {
        dependencyManager.linkConfWithTask(TEST_CONFIG, '')
    }

    @Test public void testAddFlatDirResolver() {
        FileSystemResolver expectedResolver = new FileSystemResolver()
        String expectedName = 'name'
        Object[] expectedDirs = ['a', 'b' as File]
        ResolverContainer resolverContainerMock = context.mock(ResolverContainer)
        dependencyManager.setClasspathResolvers(resolverContainerMock)
        context.checking {
            one(resolverContainerMock).createFlatDirResolver(expectedName, ['a' as File, 'b' as File] as File[]); will(returnValue(expectedResolver))
            one(resolverContainerMock).add(expectedResolver); will(returnValue(expectedResolver))
        }
        dependencyManager.addFlatDirResolver(expectedName, expectedDirs)
    }

    @Test public void testAddMavenRepo() {
        DualResolver expectedResolver = new DualResolver()
        String[] expectedJarUrls = ['http://www.somerepo.org']

        ResolverContainer resolverContainerMock = context.mock(ResolverContainer)
        dependencyManager.setClasspathResolvers(resolverContainerMock)
        context.checking {
            one(resolverContainerMock).createMavenRepoResolver(DependencyManager.DEFAULT_MAVEN_REPO_NAME,
                    DependencyManager.MAVEN_REPO_URL, expectedJarUrls);
            will(returnValue(expectedResolver))
            one(resolverContainerMock).add(expectedResolver); will(returnValue(expectedResolver))
        }
        dependencyManager.addMavenRepo(expectedJarUrls)
    }

    @Test public void testAddMavenStyleRepo() {
        DualResolver expectedResolver = new DualResolver()
        String expectedRoot = 'http://www.myroot.org'
        String expectedName = 'myname'
        String[] expectedJarUrls = ['http://www.somerepo.org']

        ResolverContainer resolverContainerMock = context.mock(ResolverContainer)
        dependencyManager.setClasspathResolvers(resolverContainerMock)
        context.checking {
            one(resolverContainerMock).createMavenRepoResolver(expectedName, expectedRoot, expectedJarUrls);
            will(returnValue(expectedResolver))
            one(resolverContainerMock).add(expectedResolver); will(returnValue(expectedResolver))
        }
        dependencyManager.addMavenStyleRepo(expectedName, expectedRoot, expectedJarUrls)
    }

    @Test public void testAddConfiguration() {
        Configuration config = dependencyManager.addConfiguration('someconf')
        assertThat(config, notNullValue())
        assertThat(config.name, equalTo('someconf'))
        assertThat(dependencyManager.configurations.someconf, sameInstance(config))
    }

    @Test public void testAddConfigurationWithConfigureClosure() {
        Configuration config = dependencyManager.addConfiguration('someconf') {
            extendsFrom 'config-a', 'config-b'
        }
        assertThat(config.name, equalTo('someconf'))
        assertThat(config.extendsFrom, equalTo(toSet('config-a', 'config-b')))
    }

    @Test public void testAddConfigurationFailsWhenConfigurationWithSameNameAlreadyExists() {
        dependencyManager.addConfiguration('config')
        try {
            dependencyManager.addConfiguration('config')
            fail()
        } catch (InvalidUserDataException e) {
            assertThat(e.message, equalTo('Cannot add configuration \'config\' as a configuration with that name already exists.'))
        }
    }

    @Test public void testGetConfiguration() {
        Configuration config = dependencyManager.addConfiguration('someconf')
        assertThat(dependencyManager.configuration('someconf'), sameInstance(config))
    }

    @Test public void testGetConfigurationFailsWhenConfigurationNotFound() {
        try {
            dependencyManager.configuration('someconf')
            fail()
        } catch (UnknownConfigurationException e) {
            assertThat(e.message, equalTo('Configuration with name \'someconf\' not found.'))
        }
    }

    @Test public void testGetConfigurationWithConfigureClosure() {
        Configuration config = dependencyManager.addConfiguration('someconf')
        Configuration retval = dependencyManager.configuration('someconf') {
            extendsFrom 'config-a', 'config-b'
        }
        assertThat(retval, sameInstance(config))
        assertThat(config.extendsFrom, equalTo(toSet('config-a', 'config-b')))
    }

    @Test public void testFindConfiguration() {
        Configuration config = dependencyManager.addConfiguration('someconf')
        assertThat(dependencyManager.findConfiguration('someconf'), sameInstance(config))
    }

    @Test public void testFindConfigurationReturnsNullWhenConfigurationNotFound() {
        assertThat(dependencyManager.findConfiguration('someconf'), nullValue())
    }

    @Test public void testMakesConfigurationAvailableAsProperty() {
        Configuration config = dependencyManager.addConfiguration('someconf')
        assertThat(dependencyManager.someconf, sameInstance(config))
    }

    @Test public void testDynamicMethodToConfigureConfiguration() {
        Configuration config = dependencyManager.addConfiguration('someconf')
        Configuration retval = dependencyManager.someconf {
            extendsFrom 'config-a', 'config-b'
        }
        assertThat(retval, sameInstance(config))
        assertThat(config.extendsFrom, equalTo(toSet('config-a', 'config-b')))
    }

    @Test public void testDynamicMethodForAddDependencyWithClosure() {
        Object testDependencyDescription = new Object();
        dependencyManager.addConfiguration(AbstractDependencyContainerTest.TEST_CONFIGURATION)
        DependencyFactory dependencyFactoryMock = context.mock(DependencyFactory)
        dependencyManager.setDependencyFactory(dependencyFactoryMock)
        boolean methodCalled = false;
        Dependency testDependency = [exclude: { arg -> methodCalled = true; null}] as Dependency
        context.checking {
            one(dependencyFactoryMock).createDependency(
                    HelperUtil.getConfMappings([AbstractDependencyContainerTest.TEST_CONFIGURATION]),
                    testDependencyDescription,
                    this.project
            ); will(returnValue(testDependency))
        }
        testDependency.exclude([:])
        Closure configureClosure = { exclude([:]) }
        testObj."$AbstractDependencyContainerTest.TEST_CONFIGURATION"(
                testDependencyDescription, configureClosure)
        assertEquals(testObj.dependencies, [testDependency])
        assertTrue(methodCalled)
    }

    @Test public void testDynamicMethodForAddDependencies() {
        dependencyManager.addConfiguration(AbstractDependencyContainerTest.TEST_CONFIGURATION)
        DependencyFactory dependencyFactoryMock = context.mock(DependencyFactory)
        dependencyManager.setDependencyFactory(dependencyFactoryMock)
        List dependencies = [[:] as Dependency, [:] as Dependency, [:] as Dependency]
        context.checking {
            2.times {int i ->
                one(dependencyFactoryMock).createDependency(
                        HelperUtil.getConfMappings([AbstractDependencyContainerTest.TEST_CONFIGURATION]),
                        AbstractDependencyContainerTest.TEST_DEPENDENCIES[i],
                        this.project
                ); will(returnValue(dependencies[i]))
            }
        }

        testObj."$AbstractDependencyContainerTest.TEST_CONFIGURATION"(
                AbstractDependencyContainerTest.TEST_DEPENDENCY_1,
                AbstractDependencyContainerTest.TEST_DEPENDENCY_2)
        assertEquals(testObj.dependencies, dependencies[0..1])
    }

    @Test (expected = MissingMethodException) public void testMethodMissingWithNonExistingConfiguration() {
        testObj.'nonExistingConfigurationName'(AbstractDependencyContainerTest.TEST_DEPENDENCY_1,
                AbstractDependencyContainerTest.TEST_DEPENDENCY_2)
    }

    @Test public void testCreateModuleRevisionId() {
        ModuleRevisionId moduleRevisionId = dependencyManager.createModuleRevisionId()
        assertEquals(dependencyManager.project.name, moduleRevisionId.name)
        assertEquals(DependencyManager.DEFAULT_VERSION, moduleRevisionId.revision)
        assertEquals(DependencyManager.DEFAULT_GROUP, moduleRevisionId.organisation)

        dependencyManager.project.version = '1.0'
        dependencyManager.project.group = 'mygroup'
        moduleRevisionId = dependencyManager.createModuleRevisionId()
        assertEquals(dependencyManager.project.name, moduleRevisionId.name)
        assertEquals(dependencyManager.project.version, moduleRevisionId.revision)
        assertEquals(dependencyManager.project.group, moduleRevisionId.organisation)
    }

    @Test public void createModuleDescriptor() {
        ModuleDescriptor testModuleDescriptor = [:] as ModuleDescriptor
        context.checking {
            one(moduleDescriptorConverter).convert(dependencyManager, true)
            will(returnValue(testModuleDescriptor))
        }
        assertSame(testModuleDescriptor, dependencyManager.createModuleDescriptor(true))
    }

    @Test public void transformerCanModifyIvyDescriptor() {
        Transformer<DefaultModuleDescriptor> transformer = context.mock(Transformer)
        context.checking {
            one(moduleDescriptorConverter).addIvyTransformer(transformer)
        }

        dependencyManager.addIvyTransformer(transformer)
    }

    @Test public void tranformationClosureCanModifyIvyModuleDescriptor() {
        Closure transformer = { it }

        context.checking {
            one(moduleDescriptorConverter).addIvyTransformer(transformer)
        }

        dependencyManager.addIvyTransformer(transformer)
    }
}
