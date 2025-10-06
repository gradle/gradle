/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.tooling.model.dsl;

import org.gradle.api.Incubating;

import java.io.File;
import java.util.List;

/**
 * This interface describes the Kotlin DSL specific part of {@link GradleDslBaseScriptModel}.
 *
 * @since 9.2.0
 */
@Incubating
public interface KotlinDslBaseScriptModel {

    /**
     * Classpath required to load Kotlin DSL script template classes.
     *
     * @since 9.2.0
     */
    @Incubating
    List<File> getScriptTemplatesClassPath();

    /**
     * The compilation classpath for Kotlin DSL scripts.
     *
     * @since 9.2.0
     */
    @Incubating
    List<File> getCompileClassPath();

    /**
     * The implicit imports that are added to all Kotlin DSL scripts.
     *
     * @since 9.2.0
     */
    @Incubating
    List<String> getImplicitImports();

    /**
     * Template classes used for interpreting Kotlin DSL scripts.
     *
     * @since 9.2.0
     */
    @Incubating
    List<String> getTemplateClassNames();

}
