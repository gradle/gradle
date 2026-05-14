/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild;

import org.gradle.api.Action;
import org.gradle.api.initialization.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for the {@code architecture { }} block in the root settings file. Defines the platforms,
 * modules and project buckets that make up the Gradle build, and records them so the architecture
 * documentation and data tasks can be generated from the same source of truth.
 */
public class GradleArchitecture {

    private final Settings settings;
    private final List<ArchitectureElementBuilder> architectureElements = new ArrayList<>();
    private final List<File> projectBaseDirs = new ArrayList<>();

    public GradleArchitecture(Settings settings) {
        this.settings = settings;
    }

    public List<ArchitectureElementBuilder> getArchitectureElements() {
        return architectureElements;
    }

    public List<File> getProjectBaseDirs() {
        return projectBaseDirs;
    }

    /**
     * Defines a top-level architecture module.
     */
    public void module(String moduleName, Action<? super ArchitectureModuleBuilder> moduleConfiguration) {
        ArchitectureModuleBuilder module = new ArchitectureModuleBuilder(moduleName, settings, projectBaseDirs);
        architectureElements.add(module);
        moduleConfiguration.execute(module);
    }

    /**
     * Defines a platform.
     */
    public PlatformBuilder platform(String platformName, Action<? super PlatformBuilder> platformConfiguration) {
        PlatformBuilder platform = new PlatformBuilder(platformName, settings, projectBaseDirs);
        architectureElements.add(platform);
        platformConfiguration.execute(platform);
        return platform;
    }

    /**
     * Defines the packaging module, for project helping package Gradle.
     */
    public void packaging(Action<? super ProjectScope> moduleConfiguration) {
        moduleConfiguration.execute(new ProjectScope("packaging", settings, projectBaseDirs));
    }

    /**
     * Defines the testing module, for project helping test Gradle.
     */
    public void testing(Action<? super ProjectScope> moduleConfiguration) {
        moduleConfiguration.execute(new ProjectScope("testing", settings, projectBaseDirs));
    }

    /**
     * Defines a bucket of unassigned projects.
     */
    public void unassigned(Action<? super ProjectScope> moduleConfiguration) {
        moduleConfiguration.execute(new ProjectScope("subprojects", settings, projectBaseDirs));
    }
}
