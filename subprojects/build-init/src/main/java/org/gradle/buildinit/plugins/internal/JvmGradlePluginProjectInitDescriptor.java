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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.util.GUtil;

public abstract class JvmGradlePluginProjectInitDescriptor extends JvmProjectInitDescriptor {
    private final DocumentationRegistry documentationRegistry;

    public JvmGradlePluginProjectInitDescriptor(DocumentationRegistry documentationRegistry) {
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder, TemplateFactory templateFactory) {
        super.generate(settings, buildScriptBuilder, templateFactory);

        String pluginId = settings.getPackageName() + "." + settings.getProjectName();
        String pluginClassName = StringUtils.capitalize(GUtil.toCamelCase(settings.getProjectName())) + "Plugin";
        String testClassName = pluginClassName + "Test";
        String functionalTestClassName = pluginClassName + "FunctionalTest";

        buildScriptBuilder
            .fileComment("This generated file contains a sample Gradle plugin project to get you started.")
            .fileComment("For more details take a look at the Writing Custom Plugins chapter in the Gradle")
            .fileComment("User Manual available at " + documentationRegistry.getDocumentationFor("custom_plugins"));
        buildScriptBuilder.plugin("Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins", "java-gradle-plugin");

        buildScriptBuilder.block(null, "sourceSets")
            .containerElement(null, "functionalTest", b -> {
            });

        buildScriptBuilder.block(null, "gradlePlugin", b -> {
            b.methodInvocation(null, "testSourceSets", buildScriptBuilder.propertyExpression("sourceSets.getByName(\"functionalTest\")"));
            b.block(null, "plugins")
            .containerElement("Define the plugin", "greeting", g -> {
                g.propertyAssignment(null, "id", pluginId);
                g.propertyAssignment(null, "implementationClass", withPackage(settings, pluginClassName));
            });
        });

        buildScriptBuilder.methodInvocation(null, "configurations.getByName(\"functionalTestImplementation\").extendsFrom", buildScriptBuilder.propertyExpression("configurations.getByName(\"testImplementation\")"));
        buildScriptBuilder.taskRegistration(null, "functionalTest", "Test", b -> {
            b.propertyAssignment(null, "testClassesDirs", buildScriptBuilder.propertyExpression("sourceSets.getByName(\"functionalTest\").output.classesDirs"));
            b.propertyAssignment(null, "classpath", buildScriptBuilder.propertyExpression("sourceSets.getByName(\"functionalTest\").runtimeClasspath"));
        });
        buildScriptBuilder.taskMethodInvocation(null, "check", "Task", "dependsOn", buildScriptBuilder.propertyExpression("tasks.getByName(\"functionalTest\")"));

        TemplateOperation sourceTemplate = sourceTemplate(settings, templateFactory, pluginId, pluginClassName);
        TemplateOperation testTemplate = testTemplate(settings, templateFactory, pluginId, testClassName);
        TemplateOperation functionalTestTemplate = functionalTestTemplate(settings, templateFactory, pluginId, functionalTestClassName);
        templateFactory.whenNoSourcesAvailable(sourceTemplate, testTemplate, functionalTestTemplate).generate();
    }

    protected abstract TemplateOperation sourceTemplate(InitSettings settings, TemplateFactory templateFactory, String pluginId, String pluginClassName);

    protected abstract TemplateOperation testTemplate(InitSettings settings, TemplateFactory templateFactory, String pluginId, String testClassName);

    protected abstract TemplateOperation functionalTestTemplate(InitSettings settings, TemplateFactory templateFactory, String pluginId, String testClassName);
}
