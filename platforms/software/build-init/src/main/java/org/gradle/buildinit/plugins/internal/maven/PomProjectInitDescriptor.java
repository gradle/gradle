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

import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.buildinit.plugins.internal.BuildConverter;
import org.gradle.buildinit.plugins.internal.InitSettings;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;
import org.gradle.util.internal.IncubationLogger;
import org.gradle.workers.WorkerExecutor;
import org.gradleinternal.buildinit.plugins.internal.maven.Maven2GradleWorkAction;
import org.gradleinternal.buildinit.plugins.internal.maven.MavenConversionException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public class PomProjectInitDescriptor implements BuildConverter {
    private final static String MAVEN_VERSION = "3.9.5";
    private final static String MAVEN_WAGON_VERSION = "3.5.3";
    private final static String MAVEN_RESOLVER_VERSION = "1.9.16";

    private final MavenSettingsProvider settingsProvider;
    private final DocumentationRegistry documentationRegistry;
    private final WorkerExecutor executor;

    private FileCollection mavenClasspath;

    public PomProjectInitDescriptor(MavenSettingsProvider mavenSettingsProvider, DocumentationRegistry documentationRegistry, WorkerExecutor executor) {
        this.settingsProvider = mavenSettingsProvider;
        this.documentationRegistry = documentationRegistry;
        this.executor = executor;
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
    public boolean supportsJavaTargets() {
        return false;
    }

    @Override
    public Set<ModularizationOption> getModularizationOptions() {
        return Collections.singleton(ModularizationOption.SINGLE_PROJECT);
    }

    @Override
    public String getSourceBuildDescription() {
        return "Maven";
    }

    @Override
    public void configureClasspath(ProjectInternal.DetachedResolver detachedResolver, ObjectFactory objects, JvmPluginServices jvmPluginServices) {
        DependencyHandler dependencies = detachedResolver.getDependencies();
        Configuration config = detachedResolver.getConfigurations().detachedConfiguration(
            dependencies.create("org.apache.maven:maven-core:" + MAVEN_VERSION),
            dependencies.create("org.apache.maven:maven-plugin-api:" + MAVEN_VERSION),
            dependencies.create("org.apache.maven:maven-compat:" + MAVEN_VERSION),
            dependencies.create("org.apache.maven.wagon:wagon-http:" + MAVEN_WAGON_VERSION),
            dependencies.create("org.apache.maven.wagon:wagon-provider-api:" + MAVEN_WAGON_VERSION),
            dependencies.create("org.apache.maven.resolver:maven-resolver-connector-basic:" + MAVEN_RESOLVER_VERSION),
            dependencies.create("org.apache.maven.resolver:maven-resolver-transport-wagon:" + MAVEN_RESOLVER_VERSION)
        );
        jvmPluginServices.configureAsRuntimeClasspath(config);
        detachedResolver.getRepositories().mavenCentral();
        mavenClasspath = config;
    }

    @Override
    public void generate(InitSettings initSettings) {
        IncubationLogger.incubatingFeatureUsed("Maven to Gradle conversion");
        try {
            Settings settings = settingsProvider.buildSettings();
            executor.classLoaderIsolation(config -> config.getClasspath().from(mavenClasspath))
                    .submit(Maven2GradleWorkAction.class, params -> {
                        params.getWorkingDir().set(initSettings.getTarget());
                        params.getDsl().set(initSettings.getDsl());
                        params.getUseIncubatingAPIs().set(initSettings.isUseIncubatingAPIs());
                        params.getMavenSettings().set(settings);
                        params.getInsecureProtocolOption().set(initSettings.getInsecureProtocolOption());
                    });
        } catch (SettingsBuildingException exception) {
            throw new MavenConversionException(String.format("Could not convert Maven POM %s to a Gradle build.", initSettings.getTarget().file("pom.xml").getAsFile()), exception);
        }
    }

    @Override
    public boolean supportsProjectName() {
        return false;
    }

    @Override
    public boolean canApplyToCurrentDirectory(Directory current) {
        return current.file("pom.xml").getAsFile().isFile();
    }

    @Override
    public Set<BuildInitDsl> getDsls() {
        return new TreeSet<>(Arrays.asList(BuildInitDsl.values()));
    }

    @Override
    public BuildInitDsl getDefaultDsl() {
        return BuildInitDsl.KOTLIN;
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
    public Optional<String> getFurtherReading(InitSettings settings) {
        return Optional.of(documentationRegistry.getDocumentationRecommendationFor("information", "migrating_from_maven"));
    }
}
