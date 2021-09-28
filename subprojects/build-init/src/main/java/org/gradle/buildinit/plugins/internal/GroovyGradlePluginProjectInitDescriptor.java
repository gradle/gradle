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
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.Language;

import java.util.Set;

public class GroovyGradlePluginProjectInitDescriptor extends JvmGradlePluginProjectInitDescriptor {
    private final TemplateLibraryVersionProvider libraryVersionProvider;

    public GroovyGradlePluginProjectInitDescriptor(TemplateLibraryVersionProvider libraryVersionProvider, DocumentationRegistry documentationRegistry) {
        super(documentationRegistry, libraryVersionProvider);
        this.libraryVersionProvider = libraryVersionProvider;
    }

    @Override
    public String getId() {
        return "groovy-gradle-plugin";
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return BuildInitTestFramework.SPOCK;
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return ImmutableSet.of(BuildInitTestFramework.SPOCK);
    }

    @Override
    public Language getLanguage() {
        return Language.GROOVY;
    }

    @Override
    public void generateProjectBuildScript(String projectName, InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        super.generateProjectBuildScript(projectName, settings, buildScriptBuilder);
        buildScriptBuilder.plugin("Apply the Groovy plugin to add support for Groovy", "groovy");
        if (!settings.isUseTestSuites()) {
            buildScriptBuilder.testImplementationDependency("Use the awesome Spock testing and specification framework",
                "org.spockframework:spock-core:" + libraryVersionProvider.getVersion("spock"));
        }
    }

    @Override
    protected TemplateOperation sourceTemplate(InitSettings settings, TemplateFactory templateFactory, String pluginId, String pluginClassName) {
        return templateFactory.fromSourceTemplate("plugin/groovy/Plugin.groovy.template", t -> {
            t.subproject(settings.getSubprojects().get(0));
            t.sourceSet("main");
            t.className(pluginClassName);
            t.binding("pluginId", pluginId);
        });
    }

    @Override
    protected TemplateOperation testTemplate(InitSettings settings, TemplateFactory templateFactory, String pluginId, String testClassName) {
        return templateFactory.fromSourceTemplate("plugin/groovy/spock/PluginTest.groovy.template", t -> {
            t.subproject(settings.getSubprojects().get(0));
            t.sourceSet("test");
            t.className(testClassName);
            t.binding("pluginId", pluginId);
        });
    }

    @Override
    protected TemplateOperation functionalTestTemplate(InitSettings settings, TemplateFactory templateFactory, String pluginId, String testClassName) {
        return templateFactory.fromSourceTemplate("plugin/groovy/spock/PluginFunctionalTest.groovy.template", t -> {
            t.subproject(settings.getSubprojects().get(0));
            t.sourceSet("functionalTest");
            t.className(testClassName);
            t.binding("pluginId", pluginId);
        });
    }
}
