/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.work;

import org.gradle.api.Incubating;

import java.io.File;

/**
 * A change to a file.
 *
 * @since 5.4
 */
@Incubating
public interface FileChange {

    /**
     * The file, which may no longer exist.
     */
    File getFile();

    /**
     * The type of change to the file.
     */
    ChangeType getChangeType();

    /**
     * The normalized path of the file, as specified by the path normalization strategy.
     *
     * <p>
     *    Examples:
     * </p>
     * <ul>
     *     <li>For {@link org.gradle.api.tasks.PathSensitivity#NAME_ONLY} this is the file name.</li>
     *     <li>For {@literal @}{@link org.gradle.api.tasks.Classpath} this is empty for Jar files and the relative path (i.e. package name) of the class files.</li>
     * </ul>
     */
    String getNormalizedPath();
}
