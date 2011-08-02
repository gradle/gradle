/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.model.idea;

import org.gradle.tooling.model.BuildableElement;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.HierarchicalElement;

import java.io.File;
import java.util.List;

/**
 * Represents information about the IntelliJ IDEA module
 *
 * @since 1.0-rc-1
 */
public interface IdeaModule extends BuildableElement, HierarchicalElement {

    /**
     * All content roots. Most idea modules have a single content root.
     *
     * @return content roots
     */
    List<File> getContentRoots();
    //TODO SF - this is no good. It requires better modelling because src/test/exclude folders are per content root

    /**
     * Returns the project of this module.
     * Alias to {@link #getProject()}
     *
     * @return idea project
     */
    IdeaProject getParent();

    /**
     * Returns the project of this module.
     * Alias to {@link #getParent()}
     *
     * @return idea project
     */
    IdeaProject getProject();

    /**
     * the folder containing module file (*.iml)
     *
     * @return module file dir
     */
    File getModuleFileDir();

    /**
     * whether current module should inherit project's output directory.
     *
     * @return inherit output dirs flag
     * @see #getOutputDir()
     * @see #getTestOutputDir()
     */
    boolean getInheritOutputDirs(); //TODO SF think about modelling as 'CompilerOutput' that contains also surrounding fields.

    /**
     * directory to store module's production classes and resources.
     *
     * @return directory to store production output. non-<code>null</code> if
     *            {@link #getInheritOutputDirs()} returns <code>'false'</code>
     */
    File getOutputDir();

    /**
     * directory to store module's test classes and resources.
     *
     * @return directory to store test output. non-<code>null</code> if
     *            {@link #getInheritOutputDirs()} returns <code>'false'</code>
     */
    File getTestOutputDir();

    /**
     * source dirs.
     *
     * @return source dirs
     */
    DomainObjectSet<? extends IdeaSourceDirectory> getSourceDirectories();

    /**
     * test dirs.
     *
     * @return test dirs
     */
    DomainObjectSet<? extends IdeaSourceDirectory> getTestDirectories();

    /**
     * exclude dirs
     *
     * @return exclude dirs
     */
    List<File> getExcludeDirectories();

    /**
     * dependencies of this module (i.e. module dependencies, library dependencies, etc.)
     *
     * @return dependencies
     */
    DomainObjectSet<? extends IdeaDependency> getDependencies();
}