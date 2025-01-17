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

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.api.plugins.internal.HelpBuiltInCommand
import org.gradle.buildinit.plugins.internal.action.InitBuiltInCommand
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.layout.BuildLayout
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.internal.FileUtils
import org.gradle.internal.logging.ToStringLogger
import org.gradle.internal.scripts.ScriptFileResolver
import org.gradle.internal.service.ServiceRegistry
import org.gradle.util.Path
import spock.lang.Specification

class DefaultSettingsLoaderTest extends Specification {
    private projectRootDir = FileUtils.canonicalize(new File("someDir"))
    private mockBuildLayout = new BuildLayout(null, projectRootDir, null, Stub(ScriptFileResolver))
    @SuppressWarnings('GroovyAssignabilityCheck')
    private mockBuildLayoutFactory = Mock(BuildLayoutFactory) {
        getLayoutFor(_) >> mockBuildLayout
    }
    private mockProjectDescriptor = Mock(DefaultProjectDescriptor) {
        getPath() >> ":"
        getProjectDir() >> mockBuildLayout.settingsDir
        getBuildFile() >> new File(mockBuildLayout.settingsDir, "build.gradle")
    }
    private mockProjectRegistry = Mock(ProjectRegistry) {
        getAllProjects() >> Collections.singleton(mockProjectDescriptor)
    }
    private startParameterInternal = new StartParameterInternal()
    private mockClassLoaderScope = Mock(ClassLoaderScope)
    private mockServiceRegistry = Mock(ServiceRegistry)
    private mockGradle = Mock(GradleInternal) {
        getStartParameter() >> startParameterInternal
        getServices() >> mockServiceRegistry
        getIdentityPath() >> Path.ROOT
        getClassLoaderScope() >> mockClassLoaderScope
    }
    private mockSettingsProcessor = Mock(SettingsProcessor) {
        // When we process, we're interested in retaining the start parameter in
        // the resulting state, so we can test it, so create a new mock and configure it
        //noinspection GroovyAssignabilityCheck
        process(mockGradle, mockBuildLayout, mockClassLoaderScope, _) >> { gradle, settingsLocation, clasLoaderScope, startParameter ->
            def mockResultSettingsScript = Mock(ScriptSource) {
                getDisplayName() >> "foo"
            }

            def mockResultSettings = Mock(SettingsInternal) {
                getProjectRegistry() >> mockProjectRegistry
                getSettingsScript() >> mockResultSettingsScript
                getStartParameter() >> startParameter
            }

            def mockResultState = Mock(SettingsState) {
                getSettings() >> mockResultSettings
            }
            return mockResultState
        }
    }

    void setup() {
        startParameterInternal.setCurrentDir( projectRootDir )
    }

    private logger = new ToStringLogger()
    private builtInCommands = [new InitBuiltInCommand(), new HelpBuiltInCommand()]
    private DefaultSettingsLoader settingsLoader = new DefaultSettingsLoader(mockSettingsProcessor, mockBuildLayoutFactory, builtInCommands, logger)

    def "running default task loads settings"() {
        when:
        def resultState = settingsLoader.findAndLoadSettings(mockGradle)

        then:
        resultState != null

        and:
        logger.toString().contains("Loading build definition for build: ':'")
    }

    def "running init uses new empty settings"() {
        given:
        startParameterInternal.setCurrentDir(mockBuildLayout.settingsDir)
        startParameterInternal.setTaskNames(["init"])

        when:
        def resultState = settingsLoader.findAndLoadSettings(mockGradle)

        then:
        resultState != null

        and:
        def resultStartParam = ((StartParameterInternal) resultState.getSettings().getStartParameter())
        resultStartParam.isUseEmptySettings()

        and:
        logger.toString().contains("Skipping loading of build definition for build: ':'")
        !logger.toString().contains("Discarding loaded settings and replacing with empty settings for build: ':'")
    }

    def "running help only loads settings once"() {
        given:
        startParameterInternal.setTaskNames(["help"])

        when:
        def resultState = settingsLoader.findAndLoadSettings(mockGradle)

        then:
        resultState != null

        and:
        def resultStartParam = ((StartParameterInternal) resultState.getSettings().getStartParameter())
        !resultStartParam.isUseEmptySettings()

        and:
        logger.toString().contains("Loading build definition for build: ':'")
        !logger.toString().contains("Discarding loaded settings and replacing with empty settings for build: ':'")
    }
}
