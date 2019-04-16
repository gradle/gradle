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

public class KotlinGradlePluginProjectInitDescriptor extends JvmGradlePluginProjectInitDescriptor {
    public KotlinGradlePluginProjectInitDescriptor(BuildScriptBuilderFactory scriptBuilderFactory, TemplateOperationFactory templateOperationFactory, FileResolver fileResolver, TemplateLibraryVersionProvider libraryVersionProvider, DocumentationRegistry documentationRegistry) {
        super(scriptBuilderFactory, templateOperationFactory, fileResolver, libraryVersionProvider, documentationRegistry);
    }

    @Override
    public String getId() {
        return "kotlin-gradle-plugin";
    }

    @Override
    public Language getLanguage() {
        return Language.KOTLIN;
    }

    @Override
    public BuildInitTestFramework getDefaultTestFramework() {
        return BuildInitTestFramework.KOTLINTEST;
    }

    @Override
    public Set<BuildInitTestFramework> getTestFrameworks() {
        return ImmutableSet.of(BuildInitTestFramework.KOTLINTEST);
    }

    @Override
    protected void generate(InitSettings settings, BuildScriptBuilder buildScriptBuilder) {
        super.generate(settings, buildScriptBuilder);

        String kotlinVersion = libraryVersionProvider.getVersion("kotlin");
        buildScriptBuilder.plugin("Apply the Kotlin JVM plugin to add support for Kotlin on the JVM.", "org.jetbrains.kotlin.jvm", kotlinVersion);
        buildScriptBuilder.
            implementationDependency("Use the Kotlin JDK 8 standard library.", "org.jetbrains.kotlin:kotlin-stdlib-jdk8");

        buildScriptBuilder
            .testImplementationDependency("Use the Kotlin test library.", "org.jetbrains.kotlin:kotlin-test")
            .testImplementationDependency("Use the Kotlin JUnit integration.", "org.jetbrains.kotlin:kotlin-test-junit");
    }

    protected TemplateOperation sourceTemplate(InitSettings settings, String pluginClassName) {
        return fromSourceTemplate("plugin/kotlin/Plugin.kt.template", settings, t -> {
            t.sourceSet("main");
            t.className(pluginClassName);
            t.binding("pluginName", settings.getProjectName());
        });
    }

    protected TemplateOperation testTemplate(InitSettings settings, String pluginClassName, String testClassName) {
        return fromSourceTemplate("plugin/kotlin/kotlintest/PluginTest.kt.template", settings, t -> {
            t.sourceSet("test");
            t.className(testClassName);
            t.binding("classUnderTest", pluginClassName);
        });
    }
}
