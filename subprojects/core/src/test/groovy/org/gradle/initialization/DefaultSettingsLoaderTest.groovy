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
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.initialization.buildsrc.BuildSourceBuilder
import org.gradle.internal.FileUtils
import org.gradle.internal.service.ServiceRegistry
import org.gradle.util.WrapUtil
import spock.lang.Specification

class DefaultSettingsLoaderTest extends Specification {

    def gradle = Mock(GradleInternal)
    def settings = Mock(SettingsInternal)
    def settingsLocation = new SettingsLocation(FileUtils.canonicalize(new File("someDir")), null);
    def startParameter = new StartParameter();
    def classLoaderScope = Mock(ClassLoaderScope)
    def settingsFinder = Mock(ISettingsFinder)
    def settingsProcessor = Mock(SettingsProcessor)
    def buildSourceBuilder = Mock(BuildSourceBuilder)
    def settingsHandler = new DefaultSettingsLoader(settingsFinder, settingsProcessor, buildSourceBuilder);

    public void findAndLoadSettingsWithExistingSettings() {
        when:
        def projectRegistry = Mock(ProjectRegistry)
        def projectDescriptor = Mock(DefaultProjectDescriptor)
        def services = Mock(ServiceRegistry)
        startParameter.setCurrentDir(settingsLocation.getSettingsDir())

        settings.getProjectRegistry() >> projectRegistry
        projectRegistry.getAllProjects() >> WrapUtil.toSet(projectDescriptor)
        projectDescriptor.getProjectDir() >> settingsLocation.settingsDir
        projectDescriptor.getBuildFile() >> new File(settingsLocation.getSettingsDir(), "build.gradle")
        gradle.getStartParameter() >> startParameter
        gradle.getServices() >> services
        settingsFinder.find(startParameter) >> settingsLocation
        1 * buildSourceBuilder.buildAndCreateClassLoader({ StartParameter sp -> sp.currentDir == new File(settingsLocation.getSettingsDir(), DefaultSettings.DEFAULT_BUILD_SRC_DIR) }) >> classLoaderScope
        1 * settingsProcessor.process(gradle, settingsLocation, classLoaderScope, startParameter) >> settings

        then:
        settingsHandler.findAndLoadSettings(gradle).is(settings)
    }

}
