/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.initialization

import org.gradle.StartParameter
import org.gradle.api.Project
import org.gradle.api.UnknownProjectException
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.FeaturePreviewsActivationFixture
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.plugins.DefaultPluginManager
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

class DefaultSettingsTest extends Specification {
    File settingsDir = new File('/somepath/root').absoluteFile
    StartParameter startParameter = new StartParameter(currentDir: new File(settingsDir, 'current'), gradleUserHomeDir: new File('gradleUserHomeDir'))
    ClassLoaderScope rootClassLoaderScope = Mock(ClassLoaderScope)
    ClassLoaderScope classLoaderScope = Mock(ClassLoaderScope)
    ScriptSource scriptSourceMock = Mock(ScriptSource)
    GradleInternal gradleMock = Mock(GradleInternal)
    ProjectDescriptorRegistry projectDescriptorRegistry = new DefaultProjectDescriptorRegistry()
    ServiceRegistryFactory serviceRegistryFactory
    FileResolver fileResolver = Mock(FileResolver)
    ScriptPluginFactory scriptPluginFactory = Mock(ScriptPluginFactory)
    ScriptHandlerFactory scriptHandlerFactory = Mock(ScriptHandlerFactory)
    ScriptHandler settingsScriptHandler = Mock(ScriptHandler)
    DefaultPluginManager pluginManager = Mock(DefaultPluginManager)
    FeaturePreviews previews = new FeaturePreviews()
    DefaultSettings settings

    def setup() {
        fileResolver.resolve(_) >> { args -> args[0].canonicalFile }

        def settingsServices = Mock(ServiceRegistry)
        settingsServices.get(FileResolver) >> fileResolver
        settingsServices.get(ScriptPluginFactory) >> scriptPluginFactory
        settingsServices.get(ScriptHandlerFactory) >> scriptHandlerFactory
        settingsServices.get(ProjectDescriptorRegistry) >> projectDescriptorRegistry
        settingsServices.get(FeaturePreviews) >> previews
        settingsServices.get(DefaultPluginManager) >>> [pluginManager, null]
        settingsServices.get(InstantiatorFactory) >> Stub(InstantiatorFactory)

        serviceRegistryFactory = Mock(ServiceRegistryFactory) {
           1 * createFor(_) >> settingsServices
        }

        settings = TestUtil.instantiatorFactory().decorateLenient().newInstance(DefaultSettings.class, serviceRegistryFactory,
                gradleMock, classLoaderScope, rootClassLoaderScope, settingsScriptHandler,
                settingsDir, scriptSourceMock, startParameter)
    }

    def 'is wired properly'() {
        expect:
        settings.startParameter == startParameter
        settings.is(settings.settings)

        settings.settingsDir == settingsDir
        settings.rootProject.projectDir == settingsDir
        settings.rootDir == settingsDir

        settings.rootProject.parent == null
        settings.rootProject.projectDir.name == settings.rootProject.name
        settings.rootProject.buildFileName == Project.DEFAULT_BUILD_FILE
        settings.gradle.is(gradleMock)
        settings.buildscript.is(settingsScriptHandler)
        settings.classLoaderScope.is(classLoaderScope)
    }

    def 'can include projects'() {
        String projectA = "a"
        String projectB = "b"
        String projectC = "c"

        when:
        settings.include([projectA, "$projectB:$projectC"] as String[])

        then:
        settings.rootProject.children.size() == 2
        testDescriptor(settings.project(":$projectA"), projectA, new File(settingsDir, projectA))
        testDescriptor(settings.project(":$projectB"), projectB, new File(settingsDir, projectB))

        settings.project(":$projectB").getChildren().size() == 1
        testDescriptor(settings.project(":$projectB:$projectC"), projectC, new File(settingsDir, "$projectB/$projectC"))
    }

    def 'can include projects flat'() {
        String projectA = "a"
        String projectB = "b"

        when:
        settings.includeFlat([projectA, projectB] as String[])

        then:
        settings.rootProject.children.size() == 2
        testDescriptor(settings.project(":" + projectA), projectA, new File(settingsDir.parentFile, projectA))
        testDescriptor(settings.project(":" + projectB), projectB, new File(settingsDir.parentFile, projectB))
    }

    void testDescriptor(DefaultProjectDescriptor descriptor, String name, File projectDir) {
        assert name == descriptor.getName()
        assert projectDir == descriptor.getProjectDir()
    }

    def 'can create project descriptor'() {
        String testName = "testname"
        File testDir = new File("testDir")

        when:
        DefaultProjectDescriptor projectDescriptor = settings.createProjectDescriptor(settings.getRootProject(), testName, testDir)

        then:
        settings.rootProject.is(projectDescriptor.parent)
        settings.projectDescriptorRegistry.is(projectDescriptor.projectDescriptorRegistry)
        testName == projectDescriptor.name
        testDir.canonicalFile == projectDescriptor.projectDir
    }

    def 'can find project by path'() {
        DefaultProjectDescriptor projectDescriptor = createTestDescriptor()

        when:
        DefaultProjectDescriptor foundProjectDescriptor = settings.project(projectDescriptor.path)

        then:
        foundProjectDescriptor.is(projectDescriptor)
    }

    def 'can find project by directory'() {
        DefaultProjectDescriptor projectDescriptor = createTestDescriptor()

        when:
        DefaultProjectDescriptor foundProjectDescriptor = settings.project(projectDescriptor.projectDir)

        then:
        foundProjectDescriptor.is(projectDescriptor)
    }

    def 'fails on unknown project path'() {
        when:
        settings.project("unknownPath")

        then:
        thrown(UnknownProjectException)
    }


    def 'fails on unknown project directory'() {
        when:
        settings.project(new File("unknownPath"))

        then:
        thrown(UnknownProjectException)
    }

    private DefaultProjectDescriptor createTestDescriptor() {
        String testName = "testname"
        File testDir = new File("testDir")
        return settings.createProjectDescriptor(settings.rootProject, testName, testDir)
    }

    def 'can get and set dynamic properties'() {
        when:
        settings.ext.dynamicProp = 'value'

        then:
        settings.dynamicProp == 'value'
    }

    def 'can get and set dynamic properties on extension'() {
        when:
        settings.extensions.dynamicProperty = 'valued'

        then:
        settings.dynamicProperty == 'valued'
    }

    def 'fails on missing property'() {
        when:
        settings.unknownProp

        then:
        thrown(MissingPropertyException)
    }

    def 'has useful toString'() {
        expect:
        settings.toString() == 'settings \'root\''
    }

    @Unroll
    def "can enable feature preview for #feature"() {
        when:
        settings.enableFeaturePreview(feature.name())
        then:
        previews.isFeatureEnabled(feature)
        where:
        feature << FeaturePreviewsActivationFixture.activeFeatures()
    }

    def 'fails when enabling an unknown feature'() {
        when:
        settings.enableFeaturePreview('UNKNOWN_FEATURE')
        then:
        IllegalArgumentException exception = thrown()
        exception.getMessage() == 'There is no feature named UNKNOWN_FEATURE'
    }
}
