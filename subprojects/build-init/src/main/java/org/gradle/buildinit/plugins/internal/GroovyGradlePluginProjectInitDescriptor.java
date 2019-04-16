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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;

import java.util.Set;

public class GroovyGradlePluginProjectInitDescriptor extends JvmGradlePluginProjectInitDescriptor {
    public GroovyGradlePluginProjectInitDescriptor(BuildScriptBuilderFactory scriptBuilderFactory, TemplateOperationFactory templateOperationFactory, FileResolver fileResolver, TemplateLibraryVersionProvider libraryVersionProvider, DocumentationRegistry documentationRegistry) {
        super(scriptBuilderFactory, templateOperationFactory, fileResolver, libraryVersionProvider, documentationRegistry);
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
    protected void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        super.generate(settings, buildScriptBuilder);
        buildScriptBuilder.plugin("Apply the Groovy plugin to add support for using Groovy source", "groovy");
        buildScriptBuilder.testImplementationDependency("Use the awesome Spock testing and specification framework",
            "org.spockframework:spock-core:" + libraryVersionProvider.getVersion("spock"));
    }

    protected TemplateOperation sourceTemplate(InitSettings settings, String pluginClassName) {
        return fromSourceTemplate("plugin/groovy/Plugin.groovy.template", settings, t -> {
            t.sourceSet("main");
            t.className(pluginClassName);
            t.binding("pluginName", settings.getProjectName());
        });
    }

    protected TemplateOperation testTemplate(InitSettings settings, String pluginClassName, String testClassName) {
        return fromSourceTemplate("plugin/groovy/spock/PluginTest.groovy.template", settings, t -> {
            t.sourceSet("test");
            t.className(testClassName);
            t.binding("classUnderTest", pluginClassName);
        });
    }
}
