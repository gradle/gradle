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

package org.gradle.buildinit.plugins.internal

import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider
import org.gradle.api.internal.file.FileResolver
import org.gradle.buildinit.plugins.internal.maven.Maven2Gradle
import org.gradle.buildinit.plugins.internal.maven.MavenConversionException
import org.gradle.buildinit.plugins.internal.maven.MavenProjectsCreator
import org.gradle.util.SingleMessageLogger

class PomProjectInitDescriptor implements ProjectInitDescriptor {
    private final MavenSettingsProvider settingsProvider
    private final FileResolver fileResolver


    PomProjectInitDescriptor(FileResolver fileResolver, MavenSettingsProvider mavenSettingsProvider) {
        this.fileResolver = fileResolver
        this.settingsProvider = mavenSettingsProvider
    }

    void generate() {
        SingleMessageLogger.incubatingFeatureUsed("Maven to Gradle conversion")
        def pom = fileResolver.resolve("pom.xml")
        try {
            def settings = settingsProvider.buildSettings()
            def mavenProjects = new MavenProjectsCreator().create(settings, pom)
            new Maven2Gradle(mavenProjects).convert()
        } catch (Exception exception) {
            throw new MavenConversionException("Could not convert Maven POM $pom to a Gradle build.", exception)
        }
    }
}
