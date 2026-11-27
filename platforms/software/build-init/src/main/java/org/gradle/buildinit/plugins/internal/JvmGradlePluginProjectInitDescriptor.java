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

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.util.internal.TextUtil;

import java.util.Optional;

public abstract class JvmGradlePluginProjectInitDescriptor extends LanguageLibraryProjectInitDescriptor {
    private final DocumentationRegistry documentationRegistry;
    private final TemplateLibraryVersionProvider libraryVersionProvider;

    public JvmGradlePluginProjectInitDescriptor(DocumentationRegistry documentationRegistry, TemplateLibraryVersionProvider libraryVersionProvider) {
        this.documentationRegistry = documentationRegistry;
        this.libraryVersionProvider = libraryVersionProvider;
    }

    @Override
    public ComponentType getComponentType() {
        return ComponentType.GRADLE_PLUGIN;
    }

    @Override
    public boolean supportsPackage() {
        return true;
    }

    @Override
    public void generateProjectBuildScript(String projectName, InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        buildScriptBuilder.repositories().mavenCentral("Use Maven Central for resolving dependencies.");

        String pluginId = settings.getPackageName() + ".greeting";
        String pluginClassName = StringUtils.capitalize(TextUtil.toCamelCase(settings.getProjectName())) + "Plugin";

        buildScriptBuilder
            .fileComment("This generated file contains a sample Gradle plugin project to get you started.")
            .fileComment(documentationRegistry.getDocumentationRecommendationFor("details on writing Custom Plugins", "custom_plugins"));

        buildScriptBuilder.plugin("Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins", "java-gradle-plugin");

        buildScriptBuilder.block(null, "gradlePlugin", b -> b.containerElement("Define the plugin", "plugins", "greeting", null, g -> {
            g.propertyAssignment(null, "id", pluginId, true);
            g.propertyAssignment(null, "implementationClass", withPackage(settings, pluginClassName), true);
        }));

        final BuildScriptBuilder.Expression functionalTestSourceSet;
        if (settings.isUseTestSuites()) {
            configureDefaultTestSuite(buildScriptBuilder, settings.getTestFramework(), libraryVersionProvider);

            addTestSuite("functionalTest", buildScriptBuilder, settings.getTestFramework(), libraryVersionProvider);
            functionalTestSourceSet = buildScriptBuilder.containerElementExpression("sourceSets", "functionalTest");
        } else {
            functionalTestSourceSet = buildScriptBuilder.createContainerElement("Add a source set for the functional test suite", "sourceSets", "functionalTest", "functionalTestSourceSet");

            BuildScriptBuilder.Expression functionalTestImplementation = buildScriptBuilder.containerElementExpression("configurations", "functionalTestImplementation");
            BuildScriptBuilder.Expression testImplementation = buildScriptBuilder.containerElementExpression("configurations", "testImplementation");
            buildScriptBuilder.methodInvocation(null, functionalTestImplementation, "extendsFrom", testImplementation);

            BuildScriptBuilder.Expression functionalTestRuntimeOnly = buildScriptBuilder.containerElementExpression("configurations", "functionalTestRuntimeOnly");
            BuildScriptBuilder.Expression testRuntimeOnly = buildScriptBuilder.containerElementExpression("configurations", "testRuntimeOnly");
            buildScriptBuilder.methodInvocation(null, functionalTestRuntimeOnly, "extendsFrom", testRuntimeOnly);

            BuildScriptBuilder.Expression functionalTest = buildScriptBuilder.taskRegistration("Add a task to run the functional tests", "functionalTest", "Test", b -> {
                b.propertyAssignment(null, "testClassesDirs", buildScriptBuilder.propertyExpression(functionalTestSourceSet, "output.classesDirs"), true);
                b.propertyAssignment(null, "classpath", buildScriptBuilder.propertyExpression(functionalTestSourceSet, "runtimeClasspath"), true);
                b.methodInvocation(null, "useJUnitPlatform");
            });
            buildScriptBuilder.taskMethodInvocation("Run the functional tests as part of `check`", "check", "Task", "dependsOn", functionalTest);
            buildScriptBuilder.taskMethodInvocation("Use JUnit Jupiter for unit tests.", "test", "Test", "useJUnitPlatform");
        }
        buildScriptBuilder.methodInvocation(null, "gradlePlugin.testSourceSets.add", functionalTestSourceSet);
    }

    @Override
    public void generateConventionPluginBuildScript(String conventionPluginName, InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
    }

    @Override
    public void generateSources(InitSettings settings, TemplateFactory templateFactory) {
        String pluginId = settings.getPackageName() + ".greeting";
        String pluginClassName = StringUtils.capitalize(TextUtil.toCamelCase(settings.getProjectName())) + "Plugin";
        String testClassName = pluginClassName + "Test";
        String functionalTestClassName = pluginClassName + "FunctionalTest";

        TemplateOperation sourceTemplate = sourceTemplate(settings, templateFactory, pluginId, pluginClassName);
        TemplateOperation testTemplate = testTemplate(settings, templateFactory, pluginId, testClassName);
        TemplateOperation functionalTestTemplate = functionalTestTemplate(settings, templateFactory, pluginId, functionalTestClassName);
        templateFactory.whenNoSourcesAvailable(sourceTemplate, testTemplate, functionalTestTemplate).generate();
    }

    @Override
    public Optional<String> getFurtherReading(InitSettings settings) {
        return Optional.of(documentationRegistry.getDocumentationRecommendationFor("information", "custom_plugins"));
    }

    // Spock tests are written in Groovy, so plugins in other languages need the Groovy plugin applied to compile them
    protected void addSpock(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        buildScriptBuilder.plugin("Apply the groovy plugin to also add support for Groovy (needed for Spock)", "groovy");
        if (!settings.isUseTestSuites()) {
            buildScriptBuilder.testImplementationDependency("Use the awesome Spock testing and specification framework",
                BuildInitDependency.of("org.spockframework:spock-core", libraryVersionProvider.getVersion("spock")));
            buildScriptBuilder.testRuntimeOnlyDependency(null,
                BuildInitDependency.of("org.junit.platform:junit-platform-launcher"));
        }
    }

    protected TemplateOperation spockTestTemplate(InitSettings settings, TemplateFactory templateFactory, String pluginId, String testClassName) {
        return spockTemplate("plugin/groovy/spock/PluginTest.groovy.template", "test", settings, templateFactory, pluginId, testClassName);
    }

    protected TemplateOperation spockFunctionalTestTemplate(InitSettings settings, TemplateFactory templateFactory, String pluginId, String testClassName) {
        return spockTemplate("plugin/groovy/spock/PluginFunctionalTest.groovy.template", "functionalTest", settings, templateFactory, pluginId, testClassName);
    }

    private static TemplateOperation spockTemplate(String template, String sourceSet, InitSettings settings, TemplateFactory templateFactory, String pluginId, String testClassName) {
        return templateFactory.fromSourceTemplate(template, t -> {
            t.subproject(settings.getSubprojects().get(0));
            t.sourceSet(sourceSet);
            t.language(Language.GROOVY);
            t.className(testClassName);
            t.binding("pluginId", pluginId);
        });
    }

    protected abstract TemplateOperation sourceTemplate(InitSettings settings, TemplateFactory templateFactory, String pluginId, String pluginClassName);

    protected abstract TemplateOperation testTemplate(InitSettings settings, TemplateFactory templateFactory, String pluginId, String testClassName);

    protected abstract TemplateOperation functionalTestTemplate(InitSettings settings, TemplateFactory templateFactory, String pluginId, String testClassName);
}
