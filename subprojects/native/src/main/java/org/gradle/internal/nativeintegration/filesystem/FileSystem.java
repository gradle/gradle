/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.internal.nativeintegration.filesystem;

import java.io.File;

/**
 * A file system accessible to Gradle.
 */
public interface FileSystem extends Chmod, Stat {
    /**
     * Default Unix permissions for directories, {@code 755}.
     */
    public static final int DEFAULT_DIR_MODE = 0755;

    /**
     * Default Unix permissions for files, {@code 644}.
     */
    public static final int DEFAULT_FILE_MODE = 0644;

    /**
     * Tells whether the file system is case sensitive.
     *
     * @return <tt>true</tt> if the file system is case sensitive, <tt>false</tt> otherwise
     */
    boolean isCaseSensitive();

    /**
     * Tells if the file system can create symbolic links. If the answer cannot be determined accurately,
     * <tt>false</tt> is returned.
     *
     * @return <tt>true</tt> if the file system can create symbolic links, <tt>false</tt> otherwise
     */
    boolean canCreateSymbolicLink();

    /**
     * Creates a symbolic link to a target file.
     *
     * @param link the link to be created
     * @param target the file to link to
     * @exception FileException if the operation fails
     */
    void createSymbolicLink(File link, File target) throws FileException;
}
