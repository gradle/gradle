/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.changedetection.digest;

import java.security.MessageDigest;
import java.io.File;

/**
 * Interface for actual digester update algorithms.
 *
 * @author Tom Eyckmans
 */
public interface DigesterUtilStrategy {
    /**
     * Called by a DigesterUtil.
     *
     * The DigesterUtil needs to make sure that the following holds true before calling this method:
     * - digester is not null
     * - file is not null
     * - the file exists
     * - the file is a file
     *
     * @param digester The digester to update.
     * @param file The file that needs it's digest calculated.
     */
    void digestFile(MessageDigest digester, File file);

    /**
     * Called by a DigesterUtil.
     *
     * The DigesterUtil needs to make sure that the following holds true before calling this method:
     * - digester is not null
     * - directory is not null
     * - the directory exists
     * - the directory is a directory
     *
     * @param digester The digester to update.
     * @param directory The directory that needs it's digest calculated.
     * @param directorySize The directory size that needs to be used during digest calculation.
     */
    void digestDirectory(MessageDigest digester, File directory, long directorySize);
}
