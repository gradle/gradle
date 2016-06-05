/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.play.plugins.ide.internal;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.plugins.PlayPluginConfigurations;
import org.gradle.plugins.ide.idea.GenerateIdeaModule;
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class PlayIdeaPlugin extends RuleSource {
    @Mutate
    public void configureIdeaModule(@Path("tasks.ideaModule") GenerateIdeaModule ideaModule,
                                    @Path("binaries.playBinary") final PlayApplicationBinarySpec playApplicationBinarySpec,
                                    @Path("buildDir") final File buildDir,
                                    ConfigurationContainer configurations,
                                    final FileResolver fileResolver) {
        IdeaModule module = ideaModule.getModule();

        module.setScopes(buildScopes(configurations));

        ConventionMapping conventionMapping = conventionMappingFor(module);

        conventionMapping.map("sourceDirs", new Callable<Set<File>>() {
            @Override
            public Set<File> call() throws Exception {
                // TODO: Assets should probably be a source set too
                Set<File> sourceDirs = Sets.newHashSet(playApplicationBinarySpec.getAssets().getAssetDirs());
                return CollectionUtils.inject(sourceDirs, playApplicationBinarySpec.getInputs(), new Action<CollectionUtils.InjectionStep<Set<File>, LanguageSourceSet>>() {
                    @Override
                    public void execute(CollectionUtils.InjectionStep<Set<File>, LanguageSourceSet> step) {
                        step.getTarget().addAll(step.getItem().getSource().getSrcDirs());
                    }
                });
            }
        });

        conventionMapping.map("testSourceDirs", new Callable<Set<File>>() {
            @Override
            public Set<File> call() throws Exception {
                // TODO: This should be modeled as a source set
                return Collections.singleton(fileResolver.resolve("test"));
            }
        });

        conventionMapping.map("singleEntryLibraries", new Callable<Map<String, Iterable<File>>>() {
            @Override
            public Map<String, Iterable<File>> call() throws Exception {
                return ImmutableMap.<String, Iterable<File>>builder().
                    put("COMPILE", Collections.singleton(playApplicationBinarySpec.getClasses().getClassesDir())).
                    put("RUNTIME", playApplicationBinarySpec.getClasses().getResourceDirs()).
                    // TODO: This should be modeled as a source set
                    put("TEST", Collections.singleton(new File(buildDir, "playBinary/testClasses"))).
                    build();
            }
        });

        module.setScalaPlatform(playApplicationBinarySpec.getTargetPlatform().getScalaPlatform());

        conventionMapping.map("targetBytecodeVersion", new Callable<JavaVersion>() {
            @Override
            public JavaVersion call() throws Exception {
                return getTargetJavaVersion(playApplicationBinarySpec);
            }
        });
        conventionMapping.map("languageLevel", new Callable<IdeaLanguageLevel>(){
            @Override
            public IdeaLanguageLevel call() throws Exception {
                return new IdeaLanguageLevel(getTargetJavaVersion(playApplicationBinarySpec));
            }
        });

        ideaModule.dependsOn(playApplicationBinarySpec.getInputs());
        ideaModule.dependsOn(playApplicationBinarySpec.getAssets());
    }

    private ConventionMapping conventionMappingFor(IdeaModule module) {
        return new DslObject(module).getConventionMapping();
    }

    private JavaVersion getTargetJavaVersion(PlayApplicationBinarySpec playApplicationBinarySpec) {
        return playApplicationBinarySpec.getTargetPlatform().getJavaPlatform().getTargetCompatibility();
    }

    private Map<String, Map<String, Collection<Configuration>>> buildScopes(ConfigurationContainer configurations) {
        return ImmutableMap.<String, Map<String, Collection<Configuration>>>builder().
            put("PROVIDED", buildScope()).
            put("COMPILE", buildScope(configurations.getByName(PlayPluginConfigurations.COMPILE_CONFIGURATION))).
            put("RUNTIME", buildScope(configurations.getByName(PlayPluginConfigurations.RUN_CONFIGURATION))).
            put("TEST", buildScope(configurations.getByName(PlayPluginConfigurations.TEST_COMPILE_CONFIGURATION))).
            build();
    }

    private Map<String, Collection<Configuration>> buildScope() {
        return buildScope(null);
    }

    private Map<String, Collection<Configuration>> buildScope(Configuration plus) {
        return ImmutableMap.<String, Collection<Configuration>>builder().
            put("plus", plus==null ? Collections.<Configuration>emptyList() : Collections.singletonList(plus)).
            put("minus", Collections.<Configuration>emptyList()).
            build();
    }
}
