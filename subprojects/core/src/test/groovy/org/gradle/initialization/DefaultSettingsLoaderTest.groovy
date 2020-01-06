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
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.layout.BuildLayout
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.internal.FileUtils
import org.gradle.internal.service.ServiceRegistry
import org.gradle.util.Path
import spock.lang.Specification

class DefaultSettingsLoaderTest extends Specification {

    def gradle = Mock(GradleInternal)
    def settings = Mock(SettingsInternal)
    def buildLayout = new BuildLayout(null, FileUtils.canonicalize(new File("someDir")), null)
    def buildLayoutFactory = Mock(BuildLayoutFactory)
    def settingsScript = Mock(ScriptSource)
    def startParameter = new StartParameterInternal()
    def classLoaderScope = Mock(ClassLoaderScope)
    def settingsProcessor = Mock(SettingsProcessor)
    def settingsHandler = new DefaultSettingsLoader(settingsProcessor, buildLayoutFactory)

    void findAndLoadSettingsWithExistingSettings() {
        when:
        def projectRegistry = Mock(ProjectRegistry)
        def projectDescriptor = Mock(DefaultProjectDescriptor) {
            getPath() >> ":"
        }
        def services = Mock(ServiceRegistry)
        startParameter.setCurrentDir(buildLayout.settingsDir)

        settings.getProjectRegistry() >> projectRegistry
        projectRegistry.getAllProjects() >> Collections.singleton(projectDescriptor)
        projectDescriptor.getProjectDir() >> buildLayout.settingsDir
        projectDescriptor.getBuildFile() >> new File(buildLayout.settingsDir, "build.gradle")
        gradle.getStartParameter() >> startParameter
        gradle.getServices() >> services
        gradle.getIdentityPath() >> Path.ROOT
        buildLayoutFactory.getLayoutFor(_) >> buildLayout
        gradle.getClassLoaderScope() >> classLoaderScope
        1 * settingsProcessor.process(gradle, buildLayout, classLoaderScope, startParameter) >> settings
        1 * settings.settingsScript >> settingsScript
        1 * settingsScript.displayName >> "foo"

        then:
        settingsHandler.findAndLoadSettings(gradle).is(settings)
    }

}
