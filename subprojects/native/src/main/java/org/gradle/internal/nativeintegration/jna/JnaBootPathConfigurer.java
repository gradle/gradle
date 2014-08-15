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

package org.gradle.internal.nativeintegration.jna;

import org.apache.commons.io.IOUtils;
import org.gradle.internal.nativeintegration.NativeIntegrationException;
import org.gradle.internal.os.OperatingSystem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class JnaBootPathConfigurer {
    /**
     * Attempts to find the jna library and copies it to a specified folder.
     * The copy operation happens only once. Sets the jna-related system property.
     *
     * This hackery is to prevent JNA from creating a shared lib in the tmp dir, as it does not clean things up.
     *
     * @param storageDir - where to store the jna library
     */
    public void configure(File storageDir) {
        String nativePrefix = OperatingSystem.current().getNativePrefix();
        File tmpDir = new File(storageDir, String.format("jna/%s", nativePrefix));
        tmpDir.mkdirs();
        String jnaLibName = OperatingSystem.current().isMacOsX() ? "libjnidispatch.jnilib" : System.mapLibraryName("jnidispatch");
        File libFile = new File(tmpDir, jnaLibName);
        if (!libFile.exists()) {
            String resourceName = "/com/sun/jna/" + nativePrefix + "/" + jnaLibName;
            try {
                InputStream lib = getClass().getResourceAsStream(resourceName);
                if (lib == null) {
                    return;
                }
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
                throw new NativeIntegrationException(String.format("Could not create JNA native library '%s'.", libFile), e);
            }
        }
        System.setProperty("jna.boot.library.path", tmpDir.getAbsolutePath());
    }
}
