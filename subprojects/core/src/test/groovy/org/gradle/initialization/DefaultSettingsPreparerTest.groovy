/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.properties.GradlePropertiesController
import org.gradle.api.plugins.internal.HelpBuiltInCommand
import org.gradle.buildinit.plugins.internal.action.InitBuiltInCommand
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.layout.BuildLayout
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.internal.build.BuildIncluder
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.internal.scripts.ScriptFileResolver
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultSettingsPreparerTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    private projectRootDir = tmpDir.testDirectory

    private mockBuildLayout = new BuildLayout(projectRootDir, null, Stub(ScriptFileResolver))
    private classLoaderScope = Stub(ClassLoaderScope)
    private startParameterInternal = new StartParameterInternal()
    private gradle = Stub(GradleInternal) {
        getStartParameter() >> startParameterInternal
        getIdentityPath() >> Path.ROOT
        getClassLoaderScope() >> classLoaderScope
        isRootBuild() >> true
        getOwner() >> Stub(BuildState)
    }

    private projectDescriptorRegistry = Stub(ProjectDescriptorRegistry) {
        getAllProjects() >> Collections.singleton(Stub(ProjectDescriptorInternal) {
            getPath() >> ":"
            getProjectDir() >> mockBuildLayout.settingsDir
            getBuildFile() >> new File(mockBuildLayout.settingsDir, "build.gradle")
        })
    }

    private SettingsState capturedState = null

    @SuppressWarnings('GroovyAssignabilityCheck')
    private settingsProcessor = Stub(SettingsProcessor) {
        // When we process, we're interested in retaining the start parameter in
        // the resulting state, so we can test it, so create a new mock and configure it
        process(gradle, mockBuildLayout, classLoaderScope, _) >> { g, settingsLocation, cls, startParameter ->
            def resultSettings = Stub(SettingsInternal) {
                getProjectRegistry() >> projectDescriptorRegistry
                getSettingsScript() >> Stub(ScriptSource) { getDisplayName() >> "foo" }
                getStartParameter() >> startParameter
                getIncludedBuilds() >> []
                getGradle() >> gradle
            }

            return Stub(SettingsState) {
                getSettings() >> resultSettings
            }
        }
    }

    private builtIns = [new InitBuiltInCommand(), new HelpBuiltInCommand()]

    private DefaultSettingsPreparer settingsPreparer = new DefaultSettingsPreparer(
        new TestBuildOperationRunner(),
        Stub(BuildOperationProgressEventEmitter),
        Stub(BuildDefinition) { getFromBuild() >> null },
        settingsProcessor,
        Stub(BuildStateRegistry),
        Stub(ProjectStateRegistry),
        Stub(BuildLayoutFactory) { getLayoutFor(_) >> mockBuildLayout },
        Stub(GradlePropertiesController),
        Stub(BuildIncluder),
        Stub(InitScriptHandler),
        builtIns,
        Stub(CacheConfigurationsInternal),
        TestUtil.problemsService(),
        Stub(JvmToolchainsConfigurationValidator)
    )

    void setup() {
        startParameterInternal.setCurrentDir(projectRootDir)
        gradle.attachSettings(_) >> { SettingsState state ->
            capturedState = state
        }
    }

    def "preparing settings for a default task loads settings state"() {
        when:
        settingsPreparer.prepareSettings(gradle)

        then:
        capturedState != null
    }

    def "preparing settings for the init task uses new empty settings"() {
        given:
        startParameterInternal.setCurrentDir(mockBuildLayout.settingsDir)
        startParameterInternal.setTaskNames(["init"])

        when:
        settingsPreparer.prepareSettings(gradle)

        then:
        capturedState != null

        and:
        def resultStartParam = ((StartParameterInternal) capturedState.getSettings().getStartParameter())
        resultStartParam.isUseEmptySettings()
    }

    def "preparing settings for the help task only loads settings once"() {
        given:
        startParameterInternal.setTaskNames(["help"])

        when:
        settingsPreparer.prepareSettings(gradle)

        then:
        capturedState != null

        and:
        def resultStartParam = ((StartParameterInternal) capturedState.getSettings().getStartParameter())
        !resultStartParam.isUseEmptySettings()
    }
}
