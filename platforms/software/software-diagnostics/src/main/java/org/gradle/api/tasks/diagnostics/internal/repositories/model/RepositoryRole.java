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

package org.gradle.api.tasks.diagnostics.internal.repositories.model;

import org.gradle.api.Incubating;
import org.jspecify.annotations.NullMarked;

/**
 * Role a repository plays in a Gradle build, as surfaced by the {@code repositories} report.
 *
 * @since 9.5
 */
@Incubating
@NullMarked
public enum RepositoryRole {
    /** Used by every project to load plugins — from {@code settings.pluginManagement.repositories}. */
    PLUGINS,
    /** Used by the settings script buildscript classpath — from {@code settings.buildscript.repositories}. */
    SETTINGS_BUILDSCRIPT_DEPENDENCIES,
    /** Used by legacy {@code apply plugin} classpath loading — from {@code project.buildscript.repositories}. */
    PROJECT_LEGACY_PLUGINS,
    /** Used by non-plugin buildscript classpath dependencies — from {@code project.buildscript.repositories}. */
    PROJECT_BUILDSCRIPT_DEPENDENCIES,
    /** Used by project dependency resolution — from {@code project.repositories} and {@code settings.dependencyResolutionManagement.repositories}. */
    PROJECT_DEPENDENCIES
}
