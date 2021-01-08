/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.nativeintegration.jansi;

import org.apache.commons.io.IOUtils;
import org.gradle.internal.IoActions;
import org.gradle.internal.nativeintegration.NativeIntegrationException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class JansiBootPathConfigurer {
    private static final String JANSI_LIBRARY_PATH_SYS_PROP = "library.jansi.path";
    private final JansiStorageLocator locator = new JansiStorageLocator();

    /**
     * Attempts to find the Jansi library and copies it to a specified folder.
     * The copy operation happens only once. Sets the Jansi-related system property.
     *
     * This hackery is to prevent Jansi from creating a shared lib in a tmp dir which is deleted when
     * the Java process finishes. To avoid performance impacts caused by Jansi's default behavior the
     * library is proactively extracted into a known directory and reused by subsequent invocations.
     *
     * @param storageDir where to store the Jansi library
     */
    public void configure(File storageDir) {
        JansiStorage jansiStorage = locator.locate(storageDir);

        if (jansiStorage != null) {
            File libFile = jansiStorage.getTargetLibFile();
            libFile.getParentFile().mkdirs();

            if (!libFile.exists()) {
                InputStream libraryInputStream = getClass().getResourceAsStream(jansiStorage.getJansiLibrary().getResourcePath());
                try {
                    if (libraryInputStream != null) {
                        copyLibrary(libraryInputStream, libFile);
                    }
                } finally {
                    IoActions.closeQuietly(libraryInputStream);
                }
            }

            System.setProperty(JANSI_LIBRARY_PATH_SYS_PROP, libFile.getParent());
        }
    }

    private void copyLibrary(InputStream lib, File libFile) {
        try {
            try {
                FileOutputStream outputStream = new FileOutputStream(libFile);

                try {
                    IOUtils.copy(lib, outputStream);
                } finally {
                    outputStream.close();
                }
            } finally {
                lib.close();
            }
        } catch (IOException e) {
            throw new NativeIntegrationException(String.format("Could not create Jansi native library '%s'.", libFile), e);
        }
    }
}
