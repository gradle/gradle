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

package org.gradle.demos.groovy;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Nested;

/**
 * The Groovy counterpart of {@link org.gradle.demos.java.JavaLibraryModel}: the imperative build-side
 * state the {@link GroovyLibraryReaction} produces and that other build logic could consume. XDCL has
 * no "build model" concept, so this is realized as a Gradle <em>Project extension</em> (registered on
 * the reaction's live {@code Project} context). It does <strong>not</strong> extend
 * {@code org.gradle.features.binding.BuildModel} — that marker belongs to the project-features
 * framework, not XDCL.
 *
 * <p>Fully managed so {@code project.getExtensions().create("groovyLibraryModel", GroovyLibraryModel.class)}
 * instantiates it and auto-creates the {@link #getClasses()} container.
 */
public interface GroovyLibraryModel {

    RegularFileProperty getJarFile();

    NamedDomainObjectContainer<GroovyClasses> getClasses();

    @Nested
    TestReports getTestReports();
}
