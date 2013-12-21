/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.test.fixtures.archive

import org.apache.commons.io.IOUtils

import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class JarTestFixture extends ZipTestFixture {
    File file

    /**
     * Asserts that the Jar file is well-formed
     */
     JarTestFixture(File file) {super(file)
         this.file = file
         isManifestPresentAndFirstEntry()
     }

    /**
     * Asserts that the given service is defined in this jar file.
     */
    def hasService(String serviceName, String serviceImpl) {
        assertFilePresent("META-INF/services/$serviceName", serviceImpl)
    }

    /**
     * Asserts that the manifest file is present and first entry in this jar file.
     */
    void isManifestPresentAndFirstEntry() {
        ZipInputStream zip = new ZipInputStream(new FileInputStream(file))
        try {
            ZipEntry zipEntry = zip.getNextEntry()

            if (zipEntry.getName().equalsIgnoreCase("META-INF/")) {
                zipEntry = zip.getNextEntry()
            }

            String firstEntryName = zipEntry.getName()
            assert firstEntryName.equalsIgnoreCase(JarFile.MANIFEST_NAME)
        }
        finally {
            IOUtils.closeQuietly(zip)
        }
    }
}
