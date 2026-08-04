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

package org.gradle.demos.checkstyle;

import org.gradle.api.file.ConfigurableFileCollection;

/**
 * The build-side output of the project-wide {@code CheckstyleTool} reaction: the single resolved
 * Checkstyle tool classpath, published as a {@code Project} extension so the per-source-set
 * {@code CheckstyleReaction} can read it back.
 *
 * Fully managed so {@code project.getExtensions().create("checkstyleModel", …)} instantiates
 * it and the {@link #getCheckstyleClasspath()} collection.
 */
public interface CheckstyleModel {

    ConfigurableFileCollection getCheckstyleClasspath();
}
