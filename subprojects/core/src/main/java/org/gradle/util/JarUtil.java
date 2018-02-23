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

package org.gradle.util;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JarUtil {
    public static boolean extractZipEntry(File jarFile, String entryName, File extractToFile) throws IOException {
        boolean entryExtracted = false;

        ZipInputStream zipStream = null;
        BufferedOutputStream extractTargetStream = null;
        try {
            zipStream = new ZipInputStream(new FileInputStream(jarFile));
            extractTargetStream = new BufferedOutputStream(new FileOutputStream(extractToFile));

            boolean classFileExtracted = false;
            boolean zipStreamEndReached = false;
            while (!classFileExtracted && !zipStreamEndReached) {
                final ZipEntry candidateZipEntry = zipStream.getNextEntry();

                if (candidateZipEntry == null) {
                    zipStreamEndReached = true;
                } else {
                    if (candidateZipEntry.getName().equals(entryName)) {
                        IOUtils.copy(zipStream, extractTargetStream);
                        classFileExtracted = true;
                        entryExtracted = true;
                    }
                }
            }
        } finally {
            IOUtils.closeQuietly(zipStream);
            IOUtils.closeQuietly(extractTargetStream);
        }

        return entryExtracted;
    }
}
