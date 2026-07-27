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

package org.gradle.performance.kotlindsl;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Applies the {@code java} plugin and creates a large number of source sets.
 *
 * <p>Each created source set contributes several configurations (e.g. {@code ...Implementation},
 * {@code ...CompileOnly}, {@code ...RuntimeOnly}, {@code ...AnnotationProcessor}), and each
 * configuration becomes its own generated Kotlin DSL type-safe accessor class. Applying this
 * plugin from a {@code build.gradle.kts} {@code plugins {}} block therefore forces a great number
 * of accessor classes to be generated for a single project.
 *
 * <p>The source set count is substituted by the performance test project generator.
 */
public class ManySourceSetsPlugin implements Plugin<Project> {

    private static final int SOURCE_SET_COUNT = ${sourceSetCount};

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        for (int i = 0; i < SOURCE_SET_COUNT; i++) {
            sourceSets.create("generated" + i);
        }
    }
}
