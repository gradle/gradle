/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.buildinit.plugins.internal;

import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider;
import org.gradle.buildinit.plugins.internal.maven.Maven2Gradle;
import org.gradle.buildinit.plugins.internal.maven.MavenConversionException;
import org.gradle.buildinit.plugins.internal.maven.MavenProjectsCreator;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.util.SingleMessageLogger;

import java.io.File;
import java.util.Set;

public class PomProjectInitDescriptor implements ProjectInitDescriptor {
    private final MavenSettingsProvider settingsProvider;
    private final PathToFileResolver fileResolver;

    public PomProjectInitDescriptor(PathToFileResolver fileResolver, MavenSettingsProvider mavenSettingsProvider) {
        this.fileResolver = fileResolver;
        this.settingsProvider = mavenSettingsProvider;
    }

    @Override
    public void generate(BuildInitTestFramework testFramework) {
        SingleMessageLogger.incubatingFeatureUsed("Maven to Gradle conversion");
        File pom = fileResolver.resolve("pom.xml");
        try {
            Settings settings = settingsProvider.buildSettings();
            Set<MavenProject> mavenProjects = new MavenProjectsCreator().create(settings, pom);
            new Maven2Gradle(mavenProjects, fileResolver.resolve(".")).convert();
        } catch (Exception exception) {
            throw new MavenConversionException(String.format("Could not convert Maven POM %s to a Gradle build.", pom), exception);
        }

    }

    @Override
    public boolean supports(BuildInitTestFramework testFramework) {
        return false;
    }
}
