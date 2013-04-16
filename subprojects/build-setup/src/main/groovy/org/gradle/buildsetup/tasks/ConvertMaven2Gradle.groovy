/*
 * Copyright 2012 the original author or authors.
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



package org.gradle.buildsetup.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
import org.gradle.api.internal.artifacts.DependencyManagementServices
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider
import org.gradle.buildsetup.plugins.internal.maven.Maven2Gradle
import org.gradle.buildsetup.plugins.internal.maven.MavenProjectsCreator
import org.gradle.api.tasks.TaskAction
import org.gradle.util.SingleMessageLogger

import javax.inject.Inject

/**
 * by Szczepan Faber, created at: 8/1/12
 */
@Incubating
class ConvertMaven2Gradle extends DefaultTask {
    private final MavenSettingsProvider settingsProvider

    @Inject
    ConvertMaven2Gradle(DependencyManagementServices managementServices) {
        this.settingsProvider = managementServices.get(MavenSettingsProvider)
    }

    @TaskAction
    void convertNow() {
        SingleMessageLogger.informAboutIncubating("Maven to Gradle conversion")

        def settings = settingsProvider.buildSettings()

        def mavenProjects = new MavenProjectsCreator().create(settings, project.file("pom.xml"))

        new Maven2Gradle(mavenProjects).convert()
    }
}
