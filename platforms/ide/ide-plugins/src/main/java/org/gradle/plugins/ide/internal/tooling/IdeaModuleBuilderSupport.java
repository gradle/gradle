/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.JavaVersion;
import org.gradle.api.NonNullApi;
import org.gradle.plugins.ide.idea.model.Dependency;
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.ModuleDependency;
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaCompilerOutput;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaContentRoot;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaDependency;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaDependencyScope;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaModuleDependency;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaSingleEntryLibraryDependency;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaSourceDirectory;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleModuleVersion;

import javax.annotation.Nullable;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@NonNullApi
public class IdeaModuleBuilderSupport {

    public static @Nullable JavaVersion convertToJavaVersion(@Nullable IdeaLanguageLevel ideaLanguageLevel) {
        if (ideaLanguageLevel == null) {
            return null;
        }
        String languageLevel = ideaLanguageLevel.getLevel();
        return JavaVersion.valueOf(languageLevel.replaceFirst("JDK", "VERSION"));
    }

    public static DefaultIdeaContentRoot buildContentRoot(IdeaModule ideaModule) {
        return new DefaultIdeaContentRoot()
            .setRootDirectory(ideaModule.getContentRoot())
            .setSourceDirectories(buildSourceDirectories(ideaModule.getSourceDirs(), ideaModule.getGeneratedSourceDirs()))
            .setTestDirectories(buildSourceDirectories(ideaModule.getTestSources().getFiles(), ideaModule.getGeneratedSourceDirs()))
            .setResourceDirectories(buildSourceDirectories(ideaModule.getResourceDirs(), ideaModule.getGeneratedSourceDirs()))
            .setTestResourceDirectories(buildSourceDirectories(ideaModule.getTestResources().getFiles(), ideaModule.getGeneratedSourceDirs()))
            .setExcludeDirectories(ideaModule.getExcludeDirs());
    }

    private static Set<DefaultIdeaSourceDirectory> buildSourceDirectories(Set<File> sourceDirs, Set<File> generatedSourceDirs) {
        Set<DefaultIdeaSourceDirectory> out = new LinkedHashSet<>();
        for (File s : sourceDirs) {
            DefaultIdeaSourceDirectory sourceDirectory = new DefaultIdeaSourceDirectory().setDirectory(s);
            if (generatedSourceDirs.contains(s)) {
                sourceDirectory.setGenerated(true);
            }
            out.add(sourceDirectory);
        }
        return out;
    }

    public static DefaultIdeaCompilerOutput buildCompilerOutput(IdeaModule ideaModule) {
        return new DefaultIdeaCompilerOutput()
            .setInheritOutputDirs(ideaModule.getInheritOutputDirs() != null ? ideaModule.getInheritOutputDirs() : false)
            .setOutputDir(ideaModule.getOutputDir())
            .setTestOutputDir(ideaModule.getTestOutputDir());
    }

    public static List<DefaultIdeaDependency> buildDependencies(Set<Dependency> resolvedDependencies) {
        List<DefaultIdeaDependency> dependencies = new LinkedList<>();
        for (Dependency dependency : resolvedDependencies) {
            if (dependency instanceof SingleEntryModuleLibrary) {
                SingleEntryModuleLibrary d = (SingleEntryModuleLibrary) dependency;
                DefaultIdeaSingleEntryLibraryDependency defaultDependency = new DefaultIdeaSingleEntryLibraryDependency()
                    .setFile(d.getLibraryFile())
                    .setSource(d.getSourceFile())
                    .setJavadoc(d.getJavadocFile())
                    .setScope(new DefaultIdeaDependencyScope(d.getScope()))
                    .setExported(d.isExported());

                if (d.getModuleVersion() != null) {
                    defaultDependency.setGradleModuleVersion(new DefaultGradleModuleVersion(d.getModuleVersion()));
                }
                dependencies.add(defaultDependency);
            } else if (dependency instanceof ModuleDependency) {
                ModuleDependency moduleDependency = (ModuleDependency) dependency;

                DefaultIdeaModuleDependency ideaModuleDependency = new DefaultIdeaModuleDependency(moduleDependency.getName())
                    .setExported(moduleDependency.isExported())
                    .setScope(new DefaultIdeaDependencyScope(moduleDependency.getScope()));

                dependencies.add(ideaModuleDependency);
            }
        }
        return dependencies;
    }

}
