/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.buildinit.plugins.internal.maven;

import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider;
import org.gradle.buildinit.plugins.internal.BuildConverter;
import org.gradle.buildinit.plugins.internal.BuildScriptBuilderFactory;
import org.gradle.buildinit.plugins.internal.InitSettings;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.util.SingleMessageLogger;

import java.io.File;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class PomProjectInitDescriptor implements BuildConverter {
    private final MavenSettingsProvider settingsProvider;
    private final BuildScriptBuilderFactory scriptBuilderFactory;
    private final DocumentationRegistry documentationRegistry;
    private final PathToFileResolver fileResolver;

    public PomProjectInitDescriptor(PathToFileResolver fileResolver, MavenSettingsProvider mavenSettingsProvider, BuildScriptBuilderFactory scriptBuilderFactory, DocumentationRegistry documentationRegistry) {
        this.fileResolver = fileResolver;
        this.settingsProvider = mavenSettingsProvider;
        this.scriptBuilderFactory = scriptBuilderFactory;
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public String getId() {
        return "pom";
    }

    @Override
    public ComponentType getComponentType() {
        return ComponentType.BASIC;
    }

    @Override
    public Language getLanguage() {
        return Language.NONE;
    }

    @Override
    public String getSourceBuildDescription() {
        return "Maven";
    }

    @Override
    public void generate(InitSettings initSettings) {
        SingleMessageLogger.incubatingFeatureUsed("Maven to Gradle conversion");
        File pom = fileResolver.resolve("pom.xml");
        try {
            Settings settings = settingsProvider.buildSettings();
            Set<MavenProject> mavenProjects = new MavenProjectsCreator().create(settings, pom);
            new Maven2Gradle(mavenProjects, fileResolver.resolve("."), scriptBuilderFactory).convert();
        } catch (Exception exception) {
            throw new MavenConversionException(String.format("Could not convert Maven POM %s to a Gradle build.", pom), exception);
        }
    }

    @Override
    public boolean supportsProjectName() {
        return false;
    }

    @Override
    public boolean canApplyToCurrentDirectory() {
        return fileResolver.resolve("pom.xml").isFile();
    }

    @Override
    public Set<BuildInitDsl> getDsls() {
        return Collections.singleton(BuildInitDsl.GROOVY);
    }

    @Override
    public BuildInitDsl getDefaultDsl() {
        return BuildInitDsl.GROOVY;
    }

    @Override
    public boolean supportsPackage() {
        return false;
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return BuildInitTestFramework.NONE;
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return Collections.singleton(BuildInitTestFramework.NONE);
    }

    @Override
    public Optional<String> getFurtherReading() {
        return Optional.of(documentationRegistry.getDocumentationFor("migrating_from_maven"));
    }
}
