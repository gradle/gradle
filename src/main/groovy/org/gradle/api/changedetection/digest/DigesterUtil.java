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

import java.io.File;
import java.security.MessageDigest;

/**
 * Utility class to calculate the digest of a file or directory based on a requested strategy.
 * 
 * @author Tom Eyckmans
 */
public interface DigesterUtil {
    /**
     * Updates the digester for a file based an the requested strategy.
     *
     * @param digester The digester to update.
     * @param file The file that needs it's digest calculated.
     */
    void digestFile(MessageDigest digester, File file);

    /**
     * Updated the digester for a directory based on the requested strategy.
     *  
     * @param digester The digester to update.
     * @param directory The directory that needs it's digest calculated.
     * @param directorySize The size of the directory to use for digest calculation.
     */
    void digestDirectory(MessageDigest digester, File directory, long directorySize);
}
