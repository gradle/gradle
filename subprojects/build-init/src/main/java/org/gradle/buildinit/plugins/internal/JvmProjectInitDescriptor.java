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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    protected String applicationConventionPlugin(InitSettings settings) {
        return settings.getPackageName() + "." + getLanguage().getName() + "-application-conventions";
    }

    protected String libraryConventionPlugin(InitSettings settings) {
        return settings.getPackageName() + "." + getLanguage().getName() + "-library-conventions";
    }

    private String commonConventionPlugin(InitSettings settings) {
        return settings.getPackageName() + "." + getLanguage().getName() + "-common-conventions";
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
    public boolean supportsPackage() {
        return true;
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return description.getDefaultTestFramework();
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return description.getSupportedTestFrameworks();
    }

    @Override
    public Optional<String> getFurtherReading(InitSettings settings) {
        String multi = isSingleProject(settings) ? "" : "_multi_project";
        return Optional.of(documentationRegistry.getSampleFor("building_" + getLanguage().getName() + "_" + getComponentType().pluralName() + multi));
    }

    @Override
    public void generateProjectBuildScript(String projectName, InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        if (isSingleProject(settings)) {
            addMavenCentral(buildScriptBuilder);
            String languagePlugin = description.getPluginName();
            if (languagePlugin != null) {
                String pluginVersionProperty = description.getPluginVersionProperty();
                String pluginVersion = pluginVersionProperty == null ? null : libraryVersionProvider.getVersion(pluginVersionProperty);
                buildScriptBuilder.plugin("Apply the " + languagePlugin + " Plugin to add support for " + getLanguage() + ".", languagePlugin, pluginVersion);
            }

            buildScriptBuilder.fileComment("This generated file contains a sample " + getLanguage() + " " + getComponentType() + " project to get you started.");
            buildScriptBuilder.fileComment("For more details take a look at the 'Building Java & JVM projects' chapter in the Gradle")
                .fileComment("User Manual available at " + documentationRegistry.getDocumentationFor("building_java_projects"));

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
    }

    @Override
    public void generateConventionPluginBuildScript(String conventionPluginName, InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        if ("common".equals(conventionPluginName)) {
            addMavenCentral(buildScriptBuilder);
            String languagePlugin = description.getPluginName() == null ? "java" : description.getPluginName();
            buildScriptBuilder.plugin("Apply the " + languagePlugin + " Plugin to add support for " + getLanguage() + ".", languagePlugin);
            addStandardDependencies(buildScriptBuilder, true);
            addDependencyConstraints(buildScriptBuilder);

            if (settings.isUseTestSuites()) {
                configureDefaultTestSuite(buildScriptBuilder, settings.getTestFramework(), libraryVersionProvider);
            } else {
                addTestFramework(settings.getTestFramework(), buildScriptBuilder);
            }
        } else {
            buildScriptBuilder.plugin("Apply the common convention plugin for shared build configuration between library and application projects.", commonConventionPlugin(settings));
            if ("library".equals(conventionPluginName)) {
                applyLibraryPlugin(buildScriptBuilder);
            }
            if ("application".equals(conventionPluginName)) {
                applyApplicationPlugin(buildScriptBuilder);
            }
        }
    }

    @Override
    public void generateSources(InitSettings settings, TemplateFactory templateFactory) {
        for (String subproject : settings.getSubprojects()) {
            List<String> sourceTemplates = new ArrayList<>();
            List<String> testSourceTemplates = new ArrayList<>();
            List<String> integrationTestSourceTemplates = new ArrayList<>();

            sourceTemplates(subproject, settings, templateFactory, sourceTemplates);
            testSourceTemplates(subproject, settings, templateFactory, testSourceTemplates);

            List<TemplateOperation> templateOps = new ArrayList<>(sourceTemplates.size() + testSourceTemplates.size() + integrationTestSourceTemplates.size());
            sourceTemplates.stream().map(t -> templateFactory.fromSourceTemplate(templatePath(t), "main", subproject, templateLanguage(t))).forEach(templateOps::add);
            testSourceTemplates.stream().map(t -> templateFactory.fromSourceTemplate(templatePath(t), "test", subproject, templateLanguage(t))).forEach(templateOps::add);
            integrationTestSourceTemplates.stream().map(t -> templateFactory.fromSourceTemplate(templatePath(t), "integrationTest", subproject, templateLanguage(t))).forEach(templateOps::add);

            templateFactory.whenNoSourcesAvailable(subproject, templateOps).generate();
        }
    }

    private String templatePath(String baseFileName) {
        return getLanguage().getName() + getComponentType().toString() + "/" + baseFileName
            + "." + templateLanguage(baseFileName).getExtension() +".template";
    }

    private Language templateLanguage(String baseFileName) {
        if (baseFileName.startsWith("groovy/")) {
            return Language.GROOVY;
        }
        return getLanguage();
    }

    protected abstract void sourceTemplates(String subproject, InitSettings settings, TemplateFactory templateFactory, List<String> templates);

    protected abstract void testSourceTemplates(String subproject, InitSettings settings, TemplateFactory templateFactory, List<String> templates);

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
        if (getLanguage() == Language.GROOVY) {
            String groovyVersion = libraryVersionProvider.getVersion("groovy");
            String groovyAllCoordinates = constraintsDefined ? "org.codehaus.groovy:groovy-all" : "org.codehaus.groovy:groovy-all:" + groovyVersion;
            buildScriptBuilder.implementationDependency("Use the latest Groovy version for building this library", groovyAllCoordinates);
        }
        if (getLanguage() == Language.KOTLIN) {
            buildScriptBuilder.dependencies().platformDependency("implementation", "Align versions of all Kotlin components", "org.jetbrains.kotlin:kotlin-bom");
            buildScriptBuilder.implementationDependency("Use the Kotlin JDK 8 standard library.", "org.jetbrains.kotlin:kotlin-stdlib-jdk8");
        }
        if (getLanguage() == Language.SCALA) {
            String scalaVersion = libraryVersionProvider.getVersion("scala");
            String scalaLibraryVersion = libraryVersionProvider.getVersion("scala-library");
            String scalaCoordinates = constraintsDefined ? "org.scala-lang:scala-library" : "org.scala-lang:scala-library:" + scalaLibraryVersion;
            buildScriptBuilder.implementationDependency("Use Scala " + scalaVersion + " in our library project", scalaCoordinates);
        }
    }

    private void addDependencyConstraints(BuildScriptBuilder buildScriptBuilder) {
        String commonsTextVersion = libraryVersionProvider.getVersion("commons-text");
        buildScriptBuilder.implementationDependencyConstraint("Define dependency versions as constraints", "org.apache.commons:commons-text:" + commonsTextVersion);

        if (getLanguage() == Language.GROOVY) {
            buildScriptBuilder.implementationDependencyConstraint(null, "org.codehaus.groovy:groovy-all:" + libraryVersionProvider.getVersion("groovy"));
        }
        if (getLanguage() == Language.KOTLIN) {
            buildScriptBuilder.dependencies().platformDependency("implementation", "Align versions of all Kotlin components", "org.jetbrains.kotlin:kotlin-bom");
            buildScriptBuilder.implementationDependencyConstraint(null, "org.jetbrains.kotlin:kotlin-stdlib-jdk8");
        }
        if (getLanguage() == Language.SCALA) {
            String scalaLibraryVersion = libraryVersionProvider.getVersion("scala-library");
            buildScriptBuilder.implementationDependencyConstraint(null, "org.scala-lang:scala-library:" + scalaLibraryVersion);
        }
    }

    private void addTestFramework(BuildInitTestFramework testFramework, BuildScriptBuilder buildScriptBuilder) {
        switch (testFramework) {
            case SPOCK:
                if (getLanguage() != Language.GROOVY) {
                    buildScriptBuilder
                        .plugin("Apply the groovy plugin to also add support for Groovy (needed for Spock)", "groovy")
                        .testImplementationDependency("Use the latest Groovy version for Spock testing",
                            "org.codehaus.groovy:groovy:" + libraryVersionProvider.getVersion("groovy"));
                }
                buildScriptBuilder.testImplementationDependency("Use the awesome Spock testing and specification framework even with Java",
                        "org.spockframework:spock-core:" + libraryVersionProvider.getVersion("spock"),
                        "junit:junit:" + libraryVersionProvider.getVersion("junit"));
                buildScriptBuilder.taskMethodInvocation(
                    "Use JUnit Platform for unit tests.",
                    "test", "Test", "useJUnitPlatform");
                break;
            case TESTNG:
                buildScriptBuilder
                    .testImplementationDependency(
                        "Use TestNG framework, also requires calling test.useTestNG() below",
                        "org.testng:testng:" + libraryVersionProvider.getVersion("testng"))
                    .taskMethodInvocation(
                        "Use TestNG for unit tests.",
                        "test", "Test", "useTestNG");
                break;
            case JUNIT_JUPITER:
                buildScriptBuilder.testImplementationDependency(
                        "Use JUnit Jupiter for testing.",
                        "org.junit.jupiter:junit-jupiter:" + libraryVersionProvider.getVersion("junit-jupiter"));

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
                    "junit:junit:" + junitVersion,
                    "org.scalatest:scalatest_" + scalaVersion + ":" + scalaTestVersion,
                    "org.scalatestplus:junit-4-13_" + scalaVersion + ":" + scalaTestPlusJunitVersion)
                    .testRuntimeOnlyDependency("Need scala-xml at test runtime",
                        "org.scala-lang.modules:scala-xml_" + scalaVersion + ":" + scalaXmlVersion);
                break;
            case KOTLINTEST:
                buildScriptBuilder.testImplementationDependency("Use the Kotlin test library.", "org.jetbrains.kotlin:kotlin-test");
                buildScriptBuilder.testImplementationDependency("Use the Kotlin JUnit integration.", "org.jetbrains.kotlin:kotlin-test-junit");
                break;
            default:
                buildScriptBuilder.testImplementationDependency("Use JUnit test framework.", "junit:junit:" + libraryVersionProvider.getVersion("junit"));
                break;
        }
    }
}
