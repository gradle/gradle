/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.buildinit.plugins.internal.model.Description;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.gradle.util.internal.GroovyDependencyUtil.groovyGroupName;

public abstract class JvmProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {

    protected final Description description;

    protected final TemplateLibraryVersionProvider libraryVersionProvider;
    private final DocumentationRegistry documentationRegistry;

    public JvmProjectInitDescriptor(Description description, TemplateLibraryVersionProvider libraryVersionProvider, DocumentationRegistry documentationRegistry) {
        this.description = description;
        this.libraryVersionProvider = libraryVersionProvider;
        this.documentationRegistry = documentationRegistry;
    }

    protected boolean isSingleProject(InitSettings settings) {
        return settings.getModularizationOption() == ModularizationOption.SINGLE_PROJECT;
    }

    protected String applicationConventionPlugin() {
        return InitSettings.CONVENTION_PLUGIN_NAME_PREFIX + "." + getLanguage().getName() + "-application-conventions";
    }

    protected String libraryConventionPlugin() {
        return InitSettings.CONVENTION_PLUGIN_NAME_PREFIX + "." + getLanguage().getName() + "-library-conventions";
    }

    private String commonConventionPlugin() {
        return InitSettings.CONVENTION_PLUGIN_NAME_PREFIX + "." + getLanguage().getName() + "-common-conventions";
    }

    @Override
    public String getId() {
        return getLanguage().getName() + "-" + getComponentType().toString();
    }

    @Override
    public Language getLanguage() {
        return description.getLanguage();
    }

    @Override
    public boolean isJvmLanguage() {
        return true;
    }

    @Override
    public boolean supportsPackage() {
        return true;
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework(ModularizationOption modularizationOption) {
        if (modularizationOption == ModularizationOption.WITH_LIBRARY_PROJECTS) {
            // This is the only supported option
            return BuildInitTestFramework.JUNIT_JUPITER;
        }
        return description.getDefaultTestFramework();
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks(ModularizationOption modularizationOption) {
        if (modularizationOption == ModularizationOption.WITH_LIBRARY_PROJECTS) {
            // This is the only supported option
            return Collections.singleton(BuildInitTestFramework.JUNIT_JUPITER);
        }
        return description.getSupportedTestFrameworks();
    }

    @Override
    public Optional<String> getFurtherReading(InitSettings settings) {
        String multi = isSingleProject(settings) ? "" : "_multi_project";
        return Optional.of(documentationRegistry.getSampleForMessage("building_" + getLanguage().getName() + "_" + getComponentType().pluralName() + multi));
    }

    @Override
    public void generateProjectBuildScript(String projectName, InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        if (!isSingleProject(settings)) {
            return;
        }

        addMavenCentral(buildScriptBuilder);

        description.getPluginName().ifPresent(languagePlugin -> {
            String pluginVersionProperty = description.getPluginVersionProperty();
            String pluginVersion = pluginVersionProperty == null ? null : libraryVersionProvider.getVersion(pluginVersionProperty);
            buildScriptBuilder.plugin("Apply the " + languagePlugin + " Plugin to add support for " + getLanguage() + ".", languagePlugin, pluginVersion, description.getExplicitPluginAlias());
        });

        settings.getJavaLanguageVersion().ifPresent(languageVersion -> {
            buildScriptBuilder.javaToolchainFor(languageVersion);
        });

        buildScriptBuilder
            .fileComment("This generated file contains a sample " + getLanguage() + " " + getComponentType() + " project to get you started.")
            .fileComment(documentationRegistry.getDocumentationRecommendationFor("details on building Java & JVM projects", "building_java_projects"));

        addStandardDependencies(buildScriptBuilder, false);

        if (settings.isUseTestSuites()) {
            // Spock test framework requires that we also have the Groovy plugin applied
            if (getLanguage() != Language.GROOVY && settings.getTestFramework() == BuildInitTestFramework.SPOCK) {
                buildScriptBuilder.plugin("Apply the groovy plugin to also add support for Groovy (needed for Spock)", "groovy");
            }
            configureDefaultTestSuite(buildScriptBuilder, settings.getTestFramework(), libraryVersionProvider);
        } else {
            addTestFramework(settings.getTestFramework(), buildScriptBuilder);
        }
    }

    @Override
    public void generateConventionPluginBuildScript(String conventionPluginName, InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        if ("common".equals(conventionPluginName)) {
            addMavenCentral(buildScriptBuilder);
            String languagePlugin = description.getPluginName().orElse("java");
            buildScriptBuilder.plugin("Apply the " + languagePlugin + " Plugin to add support for " + getLanguage() + ".", languagePlugin);
            addStandardDependencies(buildScriptBuilder, true);
            addDependencyConstraints(buildScriptBuilder);

            if (settings.isUseTestSuites()) {
                configureDefaultTestSuite(buildScriptBuilder, settings.getTestFramework(), libraryVersionProvider);
            } else {
                addTestFramework(settings.getTestFramework(), buildScriptBuilder);
            }

            settings.getJavaLanguageVersion().ifPresent(languageVersion -> {
                buildScriptBuilder.javaToolchainFor(languageVersion);
            });
        } else {
            buildScriptBuilder.plugin("Apply the common convention plugin for shared build configuration between library and application projects.", commonConventionPlugin());
            if ("library".equals(conventionPluginName)) {
                applyLibraryPlugin(buildScriptBuilder);
            } else if ("application".equals(conventionPluginName)) {
                applyApplicationPlugin(buildScriptBuilder);
            }
        }
    }

    @Override
    public void generateSources(InitSettings settings, TemplateFactory templateFactory) {
        for (String subproject : settings.getSubprojects()) {
            List<String> sourceTemplates = getSourceTemplates(subproject, settings, templateFactory);
            List<String> testSourceTemplates = getTestSourceTemplates(subproject, settings, templateFactory);

            List<TemplateOperation> templateOps = sourceTemplates.stream()
                .map(t -> templateFactory.fromSourceTemplate(templatePath(t), "main", subproject, templateLanguage(t)))
                .collect(toList());
            testSourceTemplates.stream()
                .map(t -> templateFactory.fromSourceTemplate(templatePath(t), "test", subproject, templateLanguage(t)))
                .forEach(templateOps::add);

            templateFactory.whenNoSourcesAvailable(subproject, templateOps).generate();
        }
    }

    private String templatePath(String baseFileName) {
        return getLanguage().getName() + getComponentType().toString() + "/" + baseFileName
            + "." + templateLanguage(baseFileName).getExtension() + ".template";
    }

    private Language templateLanguage(String baseFileName) {
        if (baseFileName.startsWith("groovy/")) {
            return Language.GROOVY;
        }
        return getLanguage();
    }

    protected abstract List<String> getSourceTemplates(String subproject, InitSettings settings, TemplateFactory templateFactory);

    protected abstract List<String> getTestSourceTemplates(String subproject, InitSettings settings, TemplateFactory templateFactory);

    protected void applyApplicationPlugin(BuildScriptBuilder buildScriptBuilder) {
        buildScriptBuilder.plugin(
            "Apply the application plugin to add support for building a CLI application in Java.",
            "application");
    }

    protected void applyLibraryPlugin(BuildScriptBuilder buildScriptBuilder) {
        buildScriptBuilder.plugin(
            "Apply the java-library plugin for API and implementation separation.",
            "java-library");
    }

    private void addMavenCentral(BuildScriptBuilder buildScriptBuilder) {
        buildScriptBuilder.repositories().mavenCentral("Use Maven Central for resolving dependencies.");
    }

    private void addStandardDependencies(BuildScriptBuilder buildScriptBuilder, boolean constraintsDefined) {
        switch (getLanguage()) {
            case GROOVY:
                String groovyVersion = libraryVersionProvider.getVersion("groovy");
                buildScriptBuilder.implementationDependency("Use the latest Groovy version for building this library",
                    BuildInitDependency.of(groovyGroupName(groovyVersion) + ":groovy-all", constraintsDefined ? null : groovyVersion));
                break;
            case SCALA:
                String scalaVersion = libraryVersionProvider.getVersion("scala");
                String scalaLibraryVersion = libraryVersionProvider.getVersion("scala-library");
                buildScriptBuilder.implementationDependency("Use Scala " + scalaVersion + " in our library project",
                    BuildInitDependency.of("org.scala-lang:scala-library", constraintsDefined ? null : scalaLibraryVersion));
                break;
            default:
                break;
        }
    }

    private void addDependencyConstraints(BuildScriptBuilder buildScriptBuilder) {
        String commonsTextVersion = libraryVersionProvider.getVersion("commons-text");
        buildScriptBuilder.implementationDependencyConstraint("Define dependency versions as constraints",
            BuildInitDependency.of("org.apache.commons:commons-text", commonsTextVersion));

        if (getLanguage() == Language.GROOVY) {
            String groovyVersion = libraryVersionProvider.getVersion("groovy");
            buildScriptBuilder.implementationDependencyConstraint(null, BuildInitDependency.of(groovyGroupName(groovyVersion) + ":groovy-all", groovyVersion));
        }
        if (getLanguage() == Language.SCALA) {
            String scalaLibraryVersion = libraryVersionProvider.getVersion("scala-library");
            buildScriptBuilder.implementationDependencyConstraint(null, BuildInitDependency.of("org.scala-lang:scala-library", scalaLibraryVersion));
        }
    }

    private void addTestFramework(BuildInitTestFramework testFramework, BuildScriptBuilder buildScriptBuilder) {
        switch (testFramework) {
            case SPOCK:
                if (getLanguage() != Language.GROOVY) {
                    String groovyVersion = libraryVersionProvider.getVersion("groovy");
                    buildScriptBuilder
                        .plugin("Apply the groovy plugin to also add support for Groovy (needed for Spock)", "groovy")
                        .testImplementationDependency("Use the latest Groovy version for Spock testing",
                            BuildInitDependency.of(groovyGroupName(groovyVersion) + ":groovy", groovyVersion));
                }
                buildScriptBuilder.testImplementationDependency("Use the awesome Spock testing and specification framework even with Java",
                    BuildInitDependency.of("org.spockframework:spock-core", libraryVersionProvider.getVersion("spock")),
                    BuildInitDependency.of("junit:junit", libraryVersionProvider.getVersion("junit")));
                buildScriptBuilder.testRuntimeOnlyDependency(null, BuildInitDependency.of("org.junit.platform:junit-platform-launcher"));
                buildScriptBuilder.taskMethodInvocation(
                    "Use JUnit Platform for unit tests.",
                    "test", "Test", "useJUnitPlatform");
                break;
            case TESTNG:
                buildScriptBuilder
                    .testImplementationDependency(
                        "Use TestNG framework, also requires calling test.useTestNG() below",
                        BuildInitDependency.of("org.testng:testng", libraryVersionProvider.getVersion("testng")))
                    .taskMethodInvocation(
                        "Use TestNG for unit tests.",
                        "test", "Test", "useTestNG");
                break;
            case JUNIT_JUPITER:
                buildScriptBuilder.testImplementationDependency(
                    "Use JUnit Jupiter for testing.",
                    BuildInitDependency.of("org.junit.jupiter:junit-jupiter", libraryVersionProvider.getVersion("junit-jupiter")));
                buildScriptBuilder.testRuntimeOnlyDependency(null, BuildInitDependency.of("org.junit.platform:junit-platform-launcher"));

                buildScriptBuilder.taskMethodInvocation(
                    "Use JUnit Platform for unit tests.",
                    "test", "Test", "useJUnitPlatform");
                break;
            case SCALATEST:
                String scalaVersion = libraryVersionProvider.getVersion("scala");
                String scalaTestVersion = libraryVersionProvider.getVersion("scalatest");
                String scalaTestPlusJunitVersion = libraryVersionProvider.getVersion("scalatestplus-junit");
                String junitVersion = libraryVersionProvider.getVersion("junit");
                String scalaXmlVersion = libraryVersionProvider.getVersion("scala-xml");
                buildScriptBuilder.testImplementationDependency("Use Scalatest for testing our library",
                    BuildInitDependency.of("junit:junit", junitVersion),
                    BuildInitDependency.of("org.scalatest:scalatest_" + scalaVersion, scalaTestVersion),
                    BuildInitDependency.of("org.scalatestplus:junit-4-13_" + scalaVersion, scalaTestPlusJunitVersion));
                buildScriptBuilder.testRuntimeOnlyDependency("Need scala-xml at test runtime",
                    BuildInitDependency.of("org.scala-lang.modules:scala-xml_" + scalaVersion, scalaXmlVersion));
                break;
            case KOTLINTEST:
                buildScriptBuilder.testImplementationDependency("Use the Kotlin JUnit 5 integration.", BuildInitDependency.of("org.jetbrains.kotlin:kotlin-test-junit5"));
                // TODO: Make this work with JUnit 5.6.0 again, see https://github.com/gradle/gradle/issues/13955
                buildScriptBuilder.testImplementationDependency("Use the JUnit 5 integration.",
                    BuildInitDependency.of("org.junit.jupiter:junit-jupiter-engine", libraryVersionProvider.getVersion("junit-jupiter")));
                buildScriptBuilder.testRuntimeOnlyDependency(null, BuildInitDependency.of("org.junit.platform:junit-platform-launcher"));

                buildScriptBuilder.taskMethodInvocation(
                    "Use JUnit Platform for unit tests.",
                    "test", "Test", "useJUnitPlatform");
                break;
            default:
                buildScriptBuilder.testImplementationDependency("Use JUnit test framework.",
                    BuildInitDependency.of("junit:junit", libraryVersionProvider.getVersion("junit")));
                break;
        }
    }
}
