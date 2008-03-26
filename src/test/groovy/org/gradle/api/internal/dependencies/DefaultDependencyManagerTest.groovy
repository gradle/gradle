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
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
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
import org.gradle.api.dependencies.ModuleDependency
import org.gradle.api.internal.project.DefaultProject
import org.gradle.util.HelperUtil

/**
 * @author Hans Dockter
 */
class DefaultDependencyManagerTest extends GroovyTestCase {
    static final String TEST_CONFIG = 'testConfig'
    static final String TEST_DEPENDENCY_1 = 'junit:junit:3.8.2:jar'
    static final String TEST_DEPENDENCY_2 = 'log4j:log4j:1.2.9'
    static final String TEST_DEPENDENCY_3 = 'spring:spring:2.0.0'
    static final String TEST_DEPENDENCY_4 = 'spring:spring-mock:2.1'
    static final List TEST_DEPENDENCIES = [TEST_DEPENDENCY_1, TEST_DEPENDENCY_2, TEST_DEPENDENCY_3, TEST_DEPENDENCY_4]

    DefaultDependencyManager dependencyManager
    DependencyFactory dependencyFactory
    ArtifactFactory artifactFactory
    SettingsConverter settingsConverter
    ModuleDescriptorConverter moduleDescriptorConverter
    Report2Classpath report2Classpath
    Ivy ivy
    DefaultProject project
    File projectRootDir
    File buildResolverDir

    void setUp() {
        projectRootDir = new File('path', 'root')
        project = HelperUtil.createRootProject(projectRootDir)
        project.gradleUserHome = 'gradleUserHome'
        ivy = new Ivy()
        report2Classpath = new Report2Classpath()
        settingsConverter = [:] as SettingsConverter
        dependencyFactory = new DependencyFactory()
        artifactFactory = new ArtifactFactory()
        buildResolverDir = new File('buildResolverDir')
        moduleDescriptorConverter = new ModuleDescriptorConverter()
        dependencyManager = new DefaultDependencyManager(ivy, dependencyFactory, artifactFactory, settingsConverter,
                moduleDescriptorConverter, report2Classpath, buildResolverDir)
        dependencyManager.project = project
    }

    void testDependencyManager() {
        assert dependencyManager.project.is(project)
        //        assert dependencyManager.ivy.is(ivy)
        assert dependencyManager.dependencyFactory.is(dependencyFactory)
        assert dependencyManager.artifactFactory.is(artifactFactory)
        assert dependencyManager.settingsConverter.is(settingsConverter)
        assert dependencyManager.moduleDescriptorConverter.is(moduleDescriptorConverter)
        assert dependencyManager.report2Classpath.is(report2Classpath)
        assert dependencyManager.buildResolverDir.is(buildResolverDir)
        assert dependencyManager.classpathResolvers
    }

    void testAddDepencencies() {
        MockFor dependencyFactoryMocker = new MockFor(DependencyFactory)
        List dependencies = [[:] as Dependency, [:] as Dependency, [:] as Dependency, [:] as Dependency]
        4.times {int i ->
            dependencyFactoryMocker.demand.createDependency(1..1) {Set confs, Object userDependency, DefaultProject project ->
                assertEquals([TEST_CONFIG] as Set, confs)
                assert userDependency.is(TEST_DEPENDENCIES[i])
                assert project.is(this.project)
                dependencies[i]
            }
        }
        dependencyFactoryMocker.use(dependencyFactory) {
            dependencyManager.addDependencies([TEST_CONFIG], TEST_DEPENDENCY_1, TEST_DEPENDENCY_2)
            assertEquals(dependencies[0..1], dependencyManager.dependencies)
            dependencyManager.addDependencies([TEST_CONFIG], [TEST_DEPENDENCY_3, TEST_DEPENDENCY_4])
            assertEquals(dependencies[0..3], dependencyManager.dependencies)
        }
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
            dependencyManager.addArtifacts(TEST_CONFIG, userArtifactDescriptions[0], userArtifactDescriptions[1])
            assertEquals([(TEST_CONFIG): [gradleArtifacts[0], gradleArtifacts[1]]], dependencyManager.artifacts)
            dependencyManager.addArtifacts(TEST_CONFIG, [userArtifactDescriptions[2], userArtifactDescriptions[3]])
            assertEquals([(TEST_CONFIG): [gradleArtifacts[0], gradleArtifacts[1], gradleArtifacts[2], gradleArtifacts[3]]], dependencyManager.artifacts)
        }
    }

    void testMethodMissingWithExistingConfiguration() {
        MockFor dependencyFactoryMocker = new MockFor(DependencyFactory)
        List dependencies = [[:] as Dependency, [:] as Dependency, [:] as Dependency]
        2.times {int i ->
            dependencyFactoryMocker.demand.createDependency(1..1) {Set confs, Object userDependency, DefaultProject project ->
                assertEquals([TEST_CONFIG] as Set, confs)
                assert userDependency.is(TEST_DEPENDENCIES[i])
                assert project.is(this.project)
                dependencies[i]
            }
        }
        dependencyFactoryMocker.use(dependencyFactory) {
            dependencyManager.addDependencies([TEST_CONFIG], TEST_DEPENDENCY_1, TEST_DEPENDENCY_2)
        }
        assertEquals(dependencyManager.dependencies, dependencies[0..1])
    }

    void testMethodMissingWithNonExistingConfiguration() {
        shouldFail(MissingMethodException) {
            dependencyManager.'nonExistingConfigurationName'(TEST_DEPENDENCY_1, TEST_DEPENDENCY_2)
        }
    }

    void testAddDependencyDescriptor() {
        DependencyDescriptor dependencyDescriptor = [:] as DependencyDescriptor
        dependencyManager.addDependencyDescriptors(dependencyDescriptor)
        assertEquals([dependencyDescriptor], dependencyManager.dependencyDescriptors)
        DependencyDescriptor dependencyDescriptor2 = [:] as DependencyDescriptor
        dependencyManager.addDependencyDescriptors(dependencyDescriptor2)
        assertEquals([dependencyDescriptor, dependencyDescriptor2], dependencyManager.dependencyDescriptors)
    }

    void testResolveClasspath() {
        String testConfiguration = 'compile'
        String testTaskName = 'myTask'

        ResolveReport resolveReport = new ResolveReport(new DefaultModuleDescriptor(new ModuleRevisionId(new ModuleId('org', 'name'), '1.4'), 'status', null))
        IvySettings expectedSettings = [:] as IvySettings
        MockFor settingsConverterMocker = new MockFor(SettingsConverter)
        settingsConverterMocker.demand.convert(1..1) {Collection classpathResolvers, Collection otherResolvers,
                                                      File gradleUserHome, RepositoryResolver buildResolver ->
            assertEquals(gradleUserHome, new File(project.gradleUserHome))
            assertEquals(dependencyManager.buildResolver, buildResolver)
            assertEquals(dependencyManager.classpathResolvers.resolverList, classpathResolvers)
            assertEquals([], otherResolvers)
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
            assertEquals([testConfiguration], resolveOptions.getConfs() as List)
            resolveReport
        }
        List expectedClasspath = []
        MockFor report2classpathMocker = new MockFor(Report2Classpath)
        report2classpathMocker.demand.getClasspath(1..1) {String configurationName, ResolveReport report ->
            assertEquals testConfiguration, configurationName
            assert resolveReport.is(report)
            expectedClasspath
        }
        moduleDescriptorConverterMocker.use(moduleDescriptorConverter) {
            ivyMocker.use() {
                settingsConverterMocker.use(settingsConverter) {
                    report2classpathMocker.use(report2Classpath) {
                        dependencyManager = new DefaultDependencyManager(new Ivy(), [:] as DependencyFactory, new ArtifactFactory(),
                                settingsConverter, moduleDescriptorConverter, report2Classpath, buildResolverDir)
                        dependencyManager.addConf2Tasks(testConfiguration, testTaskName)
                        dependencyManager.project = project
                        assert expectedClasspath.is(dependencyManager.resolveClasspath(testTaskName))
                        dependencyManager.addConf2Tasks(testConfiguration, testTaskName + 'XXXXX')
                    }
                }
            }
        }
    }

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
                                                      File gradleUserHome, RepositoryResolver buildResolver ->
            assertEquals(gradleUserHome, new File(project.gradleUserHome))
            assertEquals(dependencyManager.buildResolver, buildResolver)
            assertEquals(dependencyManager.classpathResolvers.resolverList, classpathResolvers)
            assertEquals(uploadResolvers.resolverList, otherResolvers)
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

    void testAddDependency() {
        MockFor dependencyFactoryMocker = new MockFor(DependencyFactory)
        String expectedId = 'someid'
        List expectedConfs = ['conf1', 'conf2']

        ModuleDependency testModuleDependency = new ModuleDependency('org:name:1.0')

        dependencyFactoryMocker.demand.createDependency(1..1) {Set confs, Object userDependencyDescriptor, DefaultProject project ->
            assertEquals(expectedConfs as Set, confs)
            assert userDependencyDescriptor.is(expectedId)
            assert project.is(this.project)
            testModuleDependency
        }
        dependencyFactoryMocker.use(dependencyFactory) {
            ModuleDependency moduleDependency = dependencyManager.addDependency(id: expectedId, confs: expectedConfs) {
                exclude([:])
            }
            assert moduleDependency.is(testModuleDependency)
            assert moduleDependency.is(dependencyManager.dependencies[0])
            assert moduleDependency.excludeRules
        }
    }

    void testConfigure() {
        Configuration testConfiguration = new Configuration('someconf')
        assert dependencyManager.configure {
            addConfiguration(testConfiguration)
        }.is(dependencyManager)
        assert dependencyManager.configurations.someconf.is(testConfiguration)
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
        FileSystemResolver buildResolver = dependencyManager.buildResolver
        // check lazy init
        assert buildResolver
        assert buildResolver.is(dependencyManager.buildResolver)
        
        assertEquals(["$buildResolverDir.absolutePath/$DependencyManager.BUILD_RESOLVER_PATTERN"],
                buildResolver.ivyPatterns)
        assertEquals(["$buildResolverDir.absolutePath/$DependencyManager.BUILD_RESOLVER_PATTERN"],
                buildResolver.artifactPatterns)
    }
}
