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



package org.gradle.api.plugins.maven

import org.gradle.api.DefaultTask
import org.gradle.api.internal.artifacts.DependencyManagementServices
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.maven.internal.Maven2Gradle
import org.gradle.api.plugins.maven.internal.MavenProjectsCreator
import org.gradle.api.tasks.TaskAction

import org.gradle.api.internal.DocumentationRegistry
import javax.inject.Inject
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider
import org.gradle.api.Incubating

/**
 * by Szczepan Faber, created at: 8/1/12
 */
@Incubating
class ConvertMaven2Gradle extends DefaultTask {

    private final static Logger LOG = Logging.getLogger(ConvertMaven2Gradle.class)
    boolean verbose
    boolean keepFile

    private final DocumentationRegistry documentationRegistry
    private final MavenSettingsProvider settingsProvider

    @Inject
    ConvertMaven2Gradle(DocumentationRegistry documentationRegistry, DependencyManagementServices managementServices) {
        this.documentationRegistry = documentationRegistry
        this.settingsProvider = managementServices.get(MavenSettingsProvider)
    }

    @TaskAction
    void convertNow() {
        LOG.lifecycle("""
---------------
Maven to Gradle conversion is an "incubating" feature, which means it is still in development.
See ${documentationRegistry.featureLifecycle} for more on "incubating" features.
Please use it, report any problems and share your feedback with us.
---------------
""")


        String[] args = []
        if (verbose) {
            args << '-verbose'
        }
        if (keepFile) {
            args << '-keepFile'
        }

        def settings = settingsProvider.buildSettings()

        def mavenProjects = new MavenProjectsCreator().create(settings, project.file("pom.xml"))

        new Maven2Gradle(mavenProjects).convert(args)
    }
}
