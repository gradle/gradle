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

import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.publish.PublishEngine
import org.apache.ivy.core.publish.PublishOptions
import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.gradle.api.DependencyManager
import org.gradle.api.InvalidUserDataException
import org.gradle.api.dependencies.Dependency
import org.gradle.api.dependencies.GradleArtifact
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.dependencies.ResolverContainer

/**
 * @author Hans Dockter
 */
class DefaultDependencyManagerTest extends AbstractDependencyContainerTest {
    static final String TEST_CONFIG = 'testConfig'

    DefaultDependencyManager dependencyManager
    SettingsConverter settingsConverter
    ModuleDescriptorConverter moduleDescriptorConverter
    Report2Classpath report2Classpath
    Ivy ivy
    File buildResolverDir
    ArtifactFactory artifactFactory
    BuildResolverHandler mockSpecialResolverHandler
    RepositoryResolver expectedBuildResolver

    public DefaultDependencyContainer getTestObj() {
        return dependencyManager
    }

    void setUp() {
        super.setUp()
        ivy = new Ivy()
        artifactFactory = new ArtifactFactory()
        report2Classpath = new Report2Classpath()
        settingsConverter = [:] as SettingsConverter
        buildResolverDir = new File('buildResolverDir')
        moduleDescriptorConverter = new ModuleDescriptorConverter()
        dependencyManager = new DefaultDependencyManager(ivy, dependencyFactory, artifactFactory, settingsConverter,
                moduleDescriptorConverter, report2Classpath, buildResolverDir)
        dependencyManager.project = project
        dependencyManager.clientModuleRegistry = [a: 'b']
        dependencyManager.defaultConfs = testDefaultConfs
        dependencyManager.chainConfigurer = {}
        expectedBuildResolver = new FileSystemResolver()
        mockSpecialResolverHandler = [getBuildResolver: {expectedBuildResolver},
                getBuildResolverDir: {buildResolverDir}] as BuildResolverHandler
        dependencyManager.buildResolverHandler = mockSpecialResolverHandler
    }

    void testInit() {
        //        assert dependencyManager.ivy.is(ivy)
        assert dependencyManager.artifactFactory.is(artifactFactory)
        assert dependencyManager.settingsConverter.is(settingsConverter)
        assert dependencyManager.moduleDescriptorConverter.is(moduleDescriptorConverter)
        assert dependencyManager.report2Classpath.is(report2Classpath)
        assert dependencyManager.buildResolverDir.is(buildResolverDir)
        assert dependencyManager.classpathResolvers
        assert dependencyManager.failForMissingDependencies
        assert dependencyManager.localReposCacheHandler.buildResolverDir.is(buildResolverDir)
        assert dependencyManager.buildResolverHandler.buildResolverDir.is(buildResolverDir)
    }

    void testAddArtifacts() {
        MockFor artifactFactoryMocker = new MockFor(ArtifactFactory)
        List userArtifactDescriptions = ['a', 'b', 'c', 'd']
        List gradleArtifacts = [[:] as GradleArtifact, [:] as GradleArtifact, [:] as GradleArtifact, [:] as GradleArtifact]
        4.times {int i ->
            artifactFactoryMocker.demand.createGradleArtifact(1..1) {Object userArtifactDescription ->
                assert userArtifactDescription.is(userArtifactDescriptions[i])
                gradleArtifacts[i]
            }
        }
        artifactFactoryMocker.use(artifactFactory) {
            testObj.addArtifacts(AbstractDependencyContainerTest.TEST_CONFIGURATION, userArtifactDescriptions[0], userArtifactDescriptions[1])
            assertEquals([(AbstractDependencyContainerTest.TEST_CONFIGURATION): [gradleArtifacts[0], gradleArtifacts[1]]], testObj.artifacts)
            testObj.addArtifacts(AbstractDependencyContainerTest.TEST_CONFIGURATION, [userArtifactDescriptions[2], userArtifactDescriptions[3]])
            assertEquals([(AbstractDependencyContainerTest.TEST_CONFIGURATION): [gradleArtifacts[0], gradleArtifacts[1], gradleArtifacts[2], gradleArtifacts[3]]], testObj.artifacts)
        }
    }

    void testResolveTask() {
        String testTaskName = 'myTask'
        List expectedClasspath = ['a']
        checkResolveClasspath(expectedClasspath) {DependencyManager dependencyManager1 ->
            dependencyManager.addConf2Tasks(TEST_CONFIG, testTaskName)
            assert expectedClasspath.is(dependencyManager.resolveTask(testTaskName))
            // check cache (we get an exception by the mock if the cache does not work
            assert expectedClasspath.is(dependencyManager.resolveTask(testTaskName))
        }
    }

    void testResolveTaskwithUnmappedTasked() {
        shouldFail(InvalidUserDataException) {
            dependencyManager.resolveTask('unmappedTask')
        }
    }

    void testResolve() {
        List expectedClasspath = ['a']
        checkResolveClasspath(expectedClasspath) {DependencyManager dependencyManager1 ->
            assert expectedClasspath.is(dependencyManager.resolve(TEST_CONFIG))
            // check cache (we get an exception by the mock if the cache does not work
            assert expectedClasspath.is(dependencyManager.resolve(TEST_CONFIG))
        }
    }

    void testAntpath() {
        List expectedClasspath = ['a', 'b']
        checkResolveClasspath(expectedClasspath) {DependencyManager dependencyManager1 ->
            assertEquals(expectedClasspath.join(':'), dependencyManager.antpath(TEST_CONFIG))
            // check cache (we get an exception by the mock if the cache does not work
            dependencyManager.antpath(TEST_CONFIG)
        }
    }

    void checkResolveClasspath(List expectedClasspath, Closure test) {
        ResolveReport resolveReport = new ResolveReport(new DefaultModuleDescriptor(
                new ModuleRevisionId(new ModuleId('org', 'name'), '1.4'), 'status', null))
        IvySettings expectedSettings = [:] as IvySettings
        MockFor settingsConverterMocker = new MockFor(SettingsConverter)
        settingsConverterMocker.demand.convert(1..1) {Collection classpathResolvers, Collection otherResolvers,
                                                      File gradleUserHome, RepositoryResolver buildResolver,
                                                      Map clientModuleDescriptorRegistry, Closure clientModuleChainConfigurer ->
            assertEquals(gradleUserHome, new File(project.gradleUserHome))
            assertEquals(dependencyManager.buildResolver, buildResolver)
            assertEquals(dependencyManager.classpathResolvers.resolverList, classpathResolvers)
            assertEquals([], otherResolvers)
            assertEquals(dependencyManager.clientModuleRegistry, clientModuleDescriptorRegistry)
            assert clientModuleChainConfigurer.is(dependencyManager.chainConfigurer)
            expectedSettings
        }

        ModuleDescriptor expectedModuleDescriptor = [:] as ModuleDescriptor
        MockFor moduleDescriptorConverterMocker = new MockFor(ModuleDescriptorConverter)
        moduleDescriptorConverterMocker.demand.convert(1..1) {DefaultDependencyManager dependencyManager ->
            assert dependencyManager.is(this.dependencyManager)
            expectedModuleDescriptor
        }

        MockFor ivyMocker = new MockFor(Ivy)
        //        ivyMocker.demand.setSettings(1..1) {IvySettings ivySettings ->
        //            assert expectedSettings.is(ivySettings)
        //        }
        ivyMocker.demand.newInstance(1..1) {IvySettings ivySettings ->
            assert expectedSettings.is(ivySettings)
            new Ivy()
        }
        ivyMocker.demand.resolve(1..1) {ModuleDescriptor moduleDescriptor, ResolveOptions resolveOptions ->
            assert moduleDescriptor.is(expectedModuleDescriptor)
            assertEquals([TEST_CONFIG], resolveOptions.getConfs() as List)
            resolveReport
        }
        MockFor report2classpathMocker = new MockFor(Report2Classpath)
        report2classpathMocker.demand.getClasspath(1..1) {String configurationName, ResolveReport report ->
            assertEquals TEST_CONFIG, configurationName
            assert resolveReport.is(report)
            expectedClasspath
        }
        moduleDescriptorConverterMocker.use(moduleDescriptorConverter) {
            ivyMocker.use() {
                settingsConverterMocker.use(settingsConverter) {
                    report2classpathMocker.use(report2Classpath) {
                        dependencyManager = new DefaultDependencyManager(new Ivy(), [:] as DependencyFactory, new ArtifactFactory(),
                                settingsConverter, moduleDescriptorConverter, report2Classpath, buildResolverDir)
                        dependencyManager.project = project
                        dependencyManager.buildResolverHandler = mockSpecialResolverHandler
                        test(dependencyManager)
                    }
                }
            }
        }
    }

    // todo Implemente this test. The ResolveReport is hard to mock (no defaul constructor, no interface). 
//    void testResolveClasspathWithUnresolvedDepencencies() {
//        String testConfiguration = 'compile'
//        String testTaskName = 'myTask'
//
//        ResolveReport resolveReport = new ResolveReport(new DefaultModuleDescriptor(
//                new ModuleRevisionId(new ModuleId('org', 'name'), '1.4'), 'status', null))
//
//        IvySettings expectedSettings = [:] as IvySettings
//
//        MockFor ivyMocker = new MockFor(Ivy)
//        //        ivyMocker.demand.setSettings(1..1) {IvySettings ivySettings ->
//        //            assert expectedSettings.is(ivySettings)
//        //        }
//        ivyMocker.demand.newInstance(1..1) {IvySettings ivySettings ->
//            assert expectedSettings.is(ivySettings)
//            new Ivy()
//        }
//        ivyMocker.demand.resolve(1..1) {ModuleDescriptor moduleDescriptor, ResolveOptions resolveOptions ->
//            assert moduleDescriptor.is(expectedModuleDescriptor)
//            assertEquals([testConfiguration], resolveOptions.getConfs() as List)
//            resolveReport
//        }
//        ivyMocker.use() {
//            dependencyManager = new DefaultDependencyManager(new Ivy(), [:] as DependencyFactory, new ArtifactFactory(),
//                    settingsConverter, moduleDescriptorConverter, report2Classpath, buildResolverDir)
//            dependencyManager.buildResolverHandler = mockSpecialResolverHandler
//            dependencyManager.addConf2Tasks(testConfiguration, testTaskName)
//            dependencyManager.project = project
//            shouldFail(GradleException) {
//                dependencyManager.resolveClasspath(testTaskName)
//            }
//        }
//
//    }

    void testPublishWithoutModuleDescriptor() {
        checkPublish(false)
    }

    void testPublishWithModuleDescriptor() {
        checkPublish(true)
    }

    void checkPublish(boolean uploadModuleDescriptor) {
        PublishEngine publishEngine
        List expectedArtifactPatterns = ['a', 'b']
        final String RESOLVER_NAME_1 = 'resolver1'
        final String RESOLVER_NAME_2 = 'resolver2'
        List expectedConfigurations = ['conf1']
        ResolverContainer uploadResolvers = new ResolverContainer()
        uploadResolvers.add([name: RESOLVER_NAME_1, url: 'http://www.url1.com'])
        uploadResolvers.add([name: RESOLVER_NAME_2, url: 'http://www.url2.com'])
        File expectedIvyFile = new File(project.buildDir, 'ivy.xml')

        boolean toIvyFileCalled = false
        ModuleDescriptor expectedModuleDescriptor = [toIvyFile: {File file ->
            assertEquals(expectedIvyFile, file)
            toIvyFileCalled = true
        }] as ModuleDescriptor


        MockFor moduleDescriptorConverterMocker = new MockFor(ModuleDescriptorConverter)
        moduleDescriptorConverterMocker.demand.convert(1..1) {DefaultDependencyManager dependencyManager ->
            assert dependencyManager.is(this.dependencyManager)
            expectedModuleDescriptor
        }

        IvySettings expectedSettings = [:] as IvySettings
        MockFor settingsConverterMocker = new MockFor(SettingsConverter)
        settingsConverterMocker.demand.convert(1..1) {Collection classpathResolvers, Collection otherResolvers,
                                                      File gradleUserHome, RepositoryResolver buildResolver,
                                                      Map clientModuleDescriptorRegistry, Closure clientModuleChainConfigurer ->
            assertEquals(gradleUserHome, new File(project.gradleUserHome))
            assertEquals(dependencyManager.buildResolver, buildResolver)
            assertEquals(dependencyManager.classpathResolvers.resolverList, classpathResolvers)
            assertEquals(uploadResolvers.resolverList, otherResolvers)
            assertEquals(dependencyManager.clientModuleRegistry, clientModuleDescriptorRegistry)
            assert clientModuleChainConfigurer.is(dependencyManager.chainConfigurer)
            expectedSettings
        }

        StubFor ivyMocker = new StubFor(Ivy)

        ivyMocker.demand.newInstance() {IvySettings ivySettings ->
            assert expectedSettings.is(ivySettings)
            new Ivy()
        }
        ivyMocker.demand.getPublishEngine(2..2) {publishEngine}

        MockFor publishEngineMocker = new MockFor(PublishEngine)
        Set calledResolvers = []
        publishEngineMocker.demand.publish(2..2) {ModuleDescriptor moduleDescriptor, Collection srcArtifactPattern,
                                                  DependencyResolver resolver, PublishOptions publishOptions ->
            assert moduleDescriptor.is(expectedModuleDescriptor)
            assertEquals(expectedArtifactPatterns.collect {new File(projectRootDir, it).absolutePath}, srcArtifactPattern)
            calledResolvers << resolver
            assert publishOptions.overwrite
            if (uploadModuleDescriptor) {
                assertEquals(expectedIvyFile.absolutePath, publishOptions.srcIvyPattern)
            } else {
                assert !publishOptions.srcIvyPattern
            }
        }

        moduleDescriptorConverterMocker.use(moduleDescriptorConverter) {
            ivyMocker.use() {
                settingsConverterMocker.use(settingsConverter) {
                    publishEngineMocker.use() {
                        publishEngine = new PublishEngine(null, null)
                        dependencyManager = new DefaultDependencyManager(new Ivy(), [:] as DependencyFactory, new ArtifactFactory(),
                                settingsConverter, moduleDescriptorConverter, report2Classpath, buildResolverDir)
                        dependencyManager.buildResolverHandler = mockSpecialResolverHandler
                        dependencyManager.artifactPatterns = expectedArtifactPatterns
                        dependencyManager.project = project
                        dependencyManager.publish(expectedConfigurations, uploadResolvers, uploadModuleDescriptor)
                    }
                }
            }
        }
        assertEquals(uploadResolvers.resolverList as Set, calledResolvers)
        if (uploadModuleDescriptor) {assert toIvyFileCalled}
    }



    void testAddConf2Tasks() {
        String testTaskName1 = 'task1'
        String testTaskName2 = 'task2'
        String testTaskName3 = 'task3'
        dependencyManager.addConf2Tasks(TEST_CONFIG, testTaskName1, testTaskName2)
        assertEquals([testTaskName1, testTaskName2] as Set, dependencyManager.conf2Tasks[TEST_CONFIG])
        assertEquals(TEST_CONFIG, dependencyManager.task2Conf[testTaskName1])
        assertEquals(TEST_CONFIG, dependencyManager.task2Conf[testTaskName2])

        dependencyManager.addConf2Tasks(TEST_CONFIG, testTaskName3)
        assertEquals([testTaskName3] as Set, dependencyManager.conf2Tasks[TEST_CONFIG])
        assertEquals(TEST_CONFIG, dependencyManager.task2Conf[testTaskName3])
        assertEquals(1, dependencyManager.task2Conf.size())
    }

    void testAddConf2TaskswithIllegalArgs() {
        shouldFail(InvalidUserDataException) {
            dependencyManager.addConf2Tasks(null, 'sometask')
        }
        shouldFail(InvalidUserDataException) {
            dependencyManager.addConf2Tasks('jsjs', null)
        }
        shouldFail(InvalidUserDataException) {
            dependencyManager.addConf2Tasks('jsjs', [] as String[])
        }
    }

    void testGetBuildResolver() {
        assert dependencyManager.buildResolver.is(expectedBuildResolver)
    }

    void testAddFlatDirResolver() {
        FileSystemResolver expectedResolver = new FileSystemResolver()
        String expectedName = 'name'
        Object[] expectedDirs = ['a', 'b' as File]
        MockFor resolverContainerMocker = new MockFor(ResolverContainer)
        resolverContainerMocker.demand.createFlatDirResolver(1..1) {String name, File[] dirs ->
            assertEquals(expectedName, name)
            assertArrayEquals(expectedDirs.collect {new File(it.toString())} as File[], dirs)
            expectedResolver
        }
        resolverContainerMocker.demand.add(1..1) {description ->
            assert description.is(expectedResolver)
        }
        resolverContainerMocker.use(dependencyManager.classpathResolvers) {
            dependencyManager.addFlatDirResolver(expectedName, expectedDirs)
        }
    }

    void testAddMavenRepo() {
        FileSystemResolver expectedResolver = new FileSystemResolver()
        String[] expectedJarUrls = ['http://www.somerepo.org']
        MockFor resolverContainerMocker = new MockFor(ResolverContainer)
        resolverContainerMocker.demand.createMavenRepoResolver(1..1) {String name, String root, String[] jarRepoUrls ->
            assertEquals(DependencyManager.DEFAULT_MAVEN_REPO_NAME, name)
            assertEquals(DependencyManager.MAVEN_REPO_URL, root)
            assertArrayEquals(expectedJarUrls, jarRepoUrls)
            expectedResolver
        }
        resolverContainerMocker.demand.add(1..1) {description ->
            assert description.is(expectedResolver)
        }
        resolverContainerMocker.use(dependencyManager.classpathResolvers) {
            dependencyManager.addMavenRepo(expectedJarUrls)
        }
    }

    void testAddMavenStyleRepo() {
        FileSystemResolver expectedResolver = new FileSystemResolver()
        String expectedRoot = 'http://www.myroot.org'
        String expectedName = 'myname'
        String[] expectedJarUrls = ['http://www.somerepo.org']
        MockFor resolverContainerMocker = new MockFor(ResolverContainer)
        resolverContainerMocker.demand.createMavenRepoResolver(1..1) {String name, String root, String[] jarRepoUrls ->
            assertEquals(expectedName, name)
            assertEquals(expectedRoot, root)
            assertArrayEquals(expectedJarUrls, jarRepoUrls)
            expectedResolver
        }
        resolverContainerMocker.demand.add(1..1) {description ->
            assert description.is(expectedResolver)
        }
        resolverContainerMocker.use(dependencyManager.classpathResolvers) {
            dependencyManager.addMavenStyleRepo(expectedName, expectedRoot, expectedJarUrls)
        }
    }

    void testAddConfiguration() {
        // todo: add test for String argument
        Configuration testConfiguration = new Configuration('someconf')
        assert testObj.configure {
            addConfiguration(testConfiguration)
        }.is(testObj)
        assert testObj.configurations.someconf.is(testConfiguration)
    }


    void testMethodMissingWithExistingConfiguration() {
        MockFor dependencyFactoryMocker = new MockFor(DependencyFactory)
        List dependencies = [[:] as Dependency, [:] as Dependency, [:] as Dependency]
        2.times {int i ->
            dependencyFactoryMocker.demand.createDependency(1..1) {Set confs, Object userDependency, DefaultProject project ->
                assertEquals([AbstractDependencyContainerTest.TEST_CONFIGURATION] as Set, confs)
                assert userDependency.is(AbstractDependencyContainerTest.TEST_DEPENDENCIES[i])
                assert project.is(this.project)
                dependencies[i]
            }
        }
        dependencyFactoryMocker.use(dependencyFactory) {
            testObj.dependencies([AbstractDependencyContainerTest.TEST_CONFIGURATION],
                    AbstractDependencyContainerTest.TEST_DEPENDENCY_1,
                    AbstractDependencyContainerTest.TEST_DEPENDENCY_2)
        }
        assertEquals(testObj.dependencies, dependencies[0..1])
    }

    void testMethodMissingWithNonExistingConfiguration() {
        shouldFail(MissingMethodException) {
            testObj.'nonExistingConfigurationName'(AbstractDependencyContainerTest.TEST_DEPENDENCY_1,
                    AbstractDependencyContainerTest.TEST_DEPENDENCY_2)
        }
    }

    void testCreateModuleRevisionId() {
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
}
