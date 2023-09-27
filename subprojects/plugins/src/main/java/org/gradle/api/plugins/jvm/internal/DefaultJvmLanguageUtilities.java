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
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.HasCompileOptions;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.internal.Cast;
import org.gradle.internal.instantiation.InstanceGenerator;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultJvmLanguageUtilities implements JvmLanguageUtilities {
    private final ProviderFactory providerFactory;
    private final ProjectInternal project;
    private final InstanceGenerator instanceGenerator;
    private final Map<ConfigurationInternal, Set<TaskProvider<?>>> configurationToCompileTasks; // ? is really AbstractCompile & HasCompileOptions

    @Inject
    public DefaultJvmLanguageUtilities(
            ProviderFactory providerFactory,
            InstanceGenerator instanceGenerator,
            ProjectInternal project
        ) {
        this.providerFactory = providerFactory;
        this.project = project;
        this.instanceGenerator = instanceGenerator;
        configurationToCompileTasks = new HashMap<>(5);
    }

    @Override
    public <COMPILE extends AbstractCompile & HasCompileOptions> void useDefaultTargetPlatformInference(Configuration configuration, TaskProvider<COMPILE> compileTask) {
        ConfigurationInternal configurationInternal = (ConfigurationInternal) configuration;
        Set<TaskProvider<COMPILE>> compileTasks = Cast.uncheckedCast(configurationToCompileTasks.computeIfAbsent(configurationInternal, key -> new HashSet<>()));
        compileTasks.add(compileTask);

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        configurationInternal.getAttributes().attributeProvider(
            TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            providerFactory.provider(() -> getDefaultTargetPlatform(configuration, java, compileTasks))
        );
    }

    private static <COMPILE extends AbstractCompile & HasCompileOptions> int getDefaultTargetPlatform(Configuration configuration, JavaPluginExtension java, Set<TaskProvider<COMPILE>> compileTasks) {
        assert !compileTasks.isEmpty();

        if (!configuration.isCanBeConsumed() && java.getAutoTargetJvmDisabled()) {
            return Integer.MAX_VALUE;
        }

        return compileTasks.stream().map(provider -> {
            COMPILE compileTask = provider.get();
            if (compileTask.getOptions().getRelease().isPresent()) {
                return compileTask.getOptions().getRelease().get();
            }

            List<String> compilerArgs = compileTask.getOptions().getCompilerArgs();
            int flagIndex = compilerArgs.indexOf("--release");

            if (flagIndex != -1 && flagIndex + 1 < compilerArgs.size()) {
                return Integer.parseInt(String.valueOf(compilerArgs.get(flagIndex + 1)));
            } else {
                return Integer.parseInt(JavaVersion.toVersion(compileTask.getTargetCompatibility()).getMajorVersion());
            }
        }).max(Comparator.naturalOrder()).get();
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
}
