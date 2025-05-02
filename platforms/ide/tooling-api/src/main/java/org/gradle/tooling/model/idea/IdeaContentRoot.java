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

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.UnsupportedMethodException;

import java.io.File;
import java.util.Set;

/**
 * Contains content root information.
 */
public interface IdeaContentRoot {

    /**
     * The content root directory.
     */
    File getRootDirectory();

    /**
     * The set of source directories.
     */
    DomainObjectSet<? extends IdeaSourceDirectory> getSourceDirectories();

    /**
     * The set of test source directories.
     */
    DomainObjectSet<? extends IdeaSourceDirectory> getTestDirectories();

    /**
     * The set of resource directories.
     * NOTE: The resources directory is only available for Java projects, otherwise it is empty set.
     * @throws UnsupportedMethodException For Gradle versions older than 4.7 where this method is not supported.
     * @since 4.7
     */
    DomainObjectSet<? extends IdeaSourceDirectory> getResourceDirectories() throws UnsupportedMethodException;


    /**
     * The set of test resource directories.
     * NOTE: The test resources directory is only available for Java projects, otherwise it is empty set.
     * @throws UnsupportedMethodException For Gradle versions older than 4.7 where this method is not supported.
     * @since 4.7
     */
    DomainObjectSet<? extends IdeaSourceDirectory> getTestResourceDirectories() throws UnsupportedMethodException;

    /**
     * The set of excluded directories.
     */
    Set<File> getExcludeDirectories();
}
