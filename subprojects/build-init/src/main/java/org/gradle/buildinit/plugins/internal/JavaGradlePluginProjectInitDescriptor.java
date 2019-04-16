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

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.util.GUtil;

import java.util.Set;

public class JavaGradlePluginProjectInitDescriptor extends JvmProjectInitDescriptor {
    public JavaGradlePluginProjectInitDescriptor(BuildScriptBuilderFactory scriptBuilderFactory, TemplateOperationFactory templateOperationFactory, FileResolver fileResolver, TemplateLibraryVersionProvider libraryVersionProvider) {
        super("java", scriptBuilderFactory, templateOperationFactory, fileResolver, libraryVersionProvider);
    }

    @Override
    public String getId() {
        return "java-gradle-plugin";
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return BuildInitTestFramework.JUNIT;
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return ImmutableSet.of(BuildInitTestFramework.JUNIT);
    }

    @Override
    protected void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        super.generate(settings, buildScriptBuilder);

        String pluginId = settings.getPackageName() + "." + settings.getProjectName();
        String pluginClassName = StringUtils.capitalize(GUtil.toCamelCase(settings.getProjectName())) + "Plugin";
        String testClassName = pluginClassName + "Test";

        buildScriptBuilder.plugin("Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins", "java-gradle-plugin");
        buildScriptBuilder
            .dependency("testImplementation", "Use JUnit test framework for unit tests", "junit:junit:" + libraryVersionProvider.getVersion("junit"));
        ScriptBlockBuilder pluginBlock = buildScriptBuilder.block(null, "gradlePlugin")
            .block(null, "plugins")
            .block("Define the plugin", "create(\"greeting\")");
        pluginBlock.propertyAssignment(null, "id", pluginId);
        pluginBlock.propertyAssignment(null, "implementationClass", settings.getPackageName() + "." + pluginClassName);

        TemplateOperation javaSourceTemplate = fromSourceTemplate("javagradleplugin/Plugin.java.template", settings, t -> {
            t.sourceSet("main");
            t.className(pluginClassName);
            t.binding("pluginName", settings.getProjectName());
        });
        TemplateOperation javaTestTemplate = fromSourceTemplate("javagradleplugin/PluginTest.java.template", settings, t -> {
            t.sourceSet("test");
            t.className(testClassName);
            t.binding("classUnderTest", pluginClassName);
        });
        whenNoSourcesAvailable(javaSourceTemplate, javaTestTemplate).generate();
    }
}
