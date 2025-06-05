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

package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.jvm.JavaVersionParser;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.provider.MergeProvider;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.internal.tasks.compile.HasCompileOptions;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.internal.JavaPluginExtensionInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.internal.Cast;
import org.gradle.internal.instantiation.InstanceGenerator;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultJvmLanguageUtilities implements JvmLanguageUtilities {

    private final ProjectInternal project;
    private final InstanceGenerator instanceGenerator;
    private final Map<ConfigurationInternal, Set<TaskProvider<?>>> configurationToCompileTasks; // The generic wildcard (`?`) == AbstractCompile & HasCompileOptions

    @Inject
    public DefaultJvmLanguageUtilities(
        InstanceGenerator instanceGenerator,
        ProjectInternal project
    ) {
        this.instanceGenerator = instanceGenerator;
        this.project = project;
        this.configurationToCompileTasks = new HashMap<>(5);
    }

    @Override
    public <COMPILE extends AbstractCompile & HasCompileOptions> void useDefaultTargetPlatformInference(Configuration configuration, TaskProvider<COMPILE> compileTask) {
        ConfigurationInternal configurationInternal = (ConfigurationInternal) configuration;

        Set<TaskProvider<?>> untypedTasks = configurationToCompileTasks.computeIfAbsent(configurationInternal, key -> new HashSet<>());
        Set<TaskProvider<COMPILE>> compileTasks = Cast.uncheckedCast(untypedTasks);
        compileTasks.add(compileTask);

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);

        Provider<Integer> targetJvmVersion = ((JavaPluginExtensionInternal) java).getAutoTargetJvm().flatMap(autoTargetJvm -> {
            if (!autoTargetJvm && !configuration.isCanBeConsumed()) {
                return Providers.of(Integer.MAX_VALUE);
            }

            return getMaxTargetJvmVersion(compileTasks);
        });

        configurationInternal.getAttributes().attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, targetJvmVersion);
    }

    @Override
    public void registerJvmLanguageSourceDirectory(SourceSet sourceSet, String name, Action<? super JvmLanguageSourceDirectoryBuilder> configuration) {
        DefaultJvmLanguageSourceDirectoryBuilder builder = instanceGenerator.newInstance(DefaultJvmLanguageSourceDirectoryBuilder.class,
            name,
            project,
            sourceSet);
        configuration.execute(builder);
        builder.build();
    }

    private static <COMPILE extends AbstractCompile & HasCompileOptions> ProviderInternal<Integer> getMaxTargetJvmVersion(Set<TaskProvider<COMPILE>> compileTasks) {
        assert !compileTasks.isEmpty();

        List<Provider<Integer>> allTargetJdkVersions = compileTasks.stream().map(taskProvider -> taskProvider.flatMap(compileTask -> {
            if (compileTask.getOptions().getRelease().isPresent()) {
                return compileTask.getOptions().getRelease();
            }

            List<String> compilerArgs = compileTask.getOptions().getCompilerArgs();
            int flagIndex = compilerArgs.indexOf("--release");

            if (flagIndex != -1 && flagIndex + 1 < compilerArgs.size()) {
                // String.valueOf() is required here since compilerArgs.get can mysteriously not return a String
                return Providers.of(Integer.parseInt(String.valueOf(compilerArgs.get(flagIndex + 1))));
            } else {
                return Providers.of(JavaVersionParser.parseMajorVersion(compileTask.getTargetCompatibility()));
            }
        })).collect(Collectors.toList());

        return new MergeProvider<>(allTargetJdkVersions).map(Collections::max);
    }

}
