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

package org.gradle.api.plugins.internal;

import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.JavaResolutionConsistency;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Base class for {@link JavaResolutionConsistency} implementations, this class provides a means
 * for ensuring that compile and runtime classpaths are consistent with each other - meaning
 * the will resolve the same versionf of dependencies present on both - for both production and test code.
 */
@NonNullApi
public abstract class AbstractJavaResolutionConsistency implements JavaResolutionConsistency {
    protected final Configuration mainCompileClasspath;
    protected final Configuration mainRuntimeClasspath;
    protected final Configuration testCompileClasspath;
    protected final Configuration testRuntimeClasspath;
    protected final SourceSetContainer sourceSets;
    protected final ConfigurationContainer configurations;

    protected AbstractJavaResolutionConsistency(Configuration mainCompileClasspath, Configuration mainRuntimeClasspath, Configuration testCompileClasspath, Configuration testRuntimeClasspath, SourceSetContainer sourceSets, ConfigurationContainer configurations) {
        this.mainCompileClasspath = mainCompileClasspath;
        this.mainRuntimeClasspath = mainRuntimeClasspath;
        this.testCompileClasspath = testCompileClasspath;
        this.testRuntimeClasspath = testRuntimeClasspath;
        this.sourceSets = sourceSets;
        this.configurations = configurations;
    }

    @Override
    public void useCompileClasspathVersions() {
        sourceSets.configureEach(this::applyCompileClasspathConsistency);
        testCompileClasspath.shouldResolveConsistentlyWith(mainCompileClasspath);
    }

    @Override
    public void useRuntimeClasspathVersions() {
        sourceSets.configureEach(this::applyRuntimeClasspathConsistency);
        testRuntimeClasspath.shouldResolveConsistentlyWith(mainRuntimeClasspath);
    }

    private void applyCompileClasspathConsistency(SourceSet sourceSet) {
        Configuration compileClasspath = findConfiguration(sourceSet.getCompileClasspathConfigurationName());
        Configuration runtimeClasspath = findConfiguration(sourceSet.getRuntimeClasspathConfigurationName());
        runtimeClasspath.shouldResolveConsistentlyWith(compileClasspath);
    }

    private void applyRuntimeClasspathConsistency(SourceSet sourceSet) {
        Configuration compileClasspath = findConfiguration(sourceSet.getCompileClasspathConfigurationName());
        Configuration runtimeClasspath = findConfiguration(sourceSet.getRuntimeClasspathConfigurationName());
        compileClasspath.shouldResolveConsistentlyWith(runtimeClasspath);
    }

    private Configuration findConfiguration(String configName) {
        return configurations.getByName(configName);
    }
}
