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

import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.io.IOUtils
import org.gradle.api.JavaVersion
import org.gradle.internal.lazy.Lazy
import org.gradle.internal.serialize.JavaClassUtil

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.Manifest

class JarTestFixture extends ZipTestFixture {
    File file

    private final Lazy<Manifest> theManifest = Lazy.atomic().of {
        new Manifest(IOUtils.toInputStream(content('META-INF/MANIFEST.MF'), contentCharset as String))
    }

    /**
     * Asserts that the Jar file is well-formed.
     */
    JarTestFixture(File file, String metadataCharset = 'UTF-8', String contentCharset = null) {
        this(file, metadataCharset, contentCharset, true)
    }

    /**
     * Creates the fixture.
     */
    JarTestFixture(File file, String metadataCharset, String contentCharset, boolean checkManifest) {
         super(file, metadataCharset, contentCharset)
         this.file = file
         if (checkManifest) {
             assertManifestPresentAndFirstEntry()
         }
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
    void assertManifestPresentAndFirstEntry() {
        def zipFile = new ZipFile(file, metadataCharset)
        try {
            def entries = zipFile.getEntries()
            def zipEntry = entries.nextElement()
            if(zipEntry.getName().equalsIgnoreCase('META-INF/')) {
                zipEntry = entries.nextElement()
            }
            def firstEntryName = zipEntry.getName()
            assert firstEntryName.equalsIgnoreCase(JarFile.MANIFEST_NAME)
        } finally {
            zipFile.close()
        }
    }

    @Override
    def hasDescendants(String... relativePaths) {
        String[] allDescendants = relativePaths + JarFile.MANIFEST_NAME
        return super.hasDescendants(allDescendants)
    }

    JavaVersion getJavaVersion() {
        JarFile jarFile = new JarFile(file)
        try {
            //take the first class file
            JarEntry classEntry = jarFile.entries().find { entry -> entry.name.endsWith(".class") }
            if (classEntry == null) {
                throw new Exception("Could not find a class entry for: " + file)
            }
            return JavaVersion.forClassVersion(JavaClassUtil.getClassMajorVersion(jarFile.getInputStream(classEntry)))
        } finally {
            jarFile.close()
        }
    }

    Manifest getManifest() {
        return theManifest.get()
    }

    def assertIsMultiRelease() {
        assert "true" == manifest.mainAttributes.getValue("Multi-Release")
        this
    }

    def assertContainsVersioned(int version, String basePath) {
        assertContainsFile(toVersionedPath(version, basePath))
        this
    }

    def assertNotContainsVersioned(int version, String basePath) {
        assertNotContainsFile(toVersionedPath(version, basePath))
    }

    def assertVersionedContent(int version, String basePath, String content) {
        assertFileContent(toVersionedPath(version, basePath), content)
    }

    static String toVersionedPath(int version, String basePath) {
        assert version > 8: "Java only supports versioned directories for versions >8, got $version"
        return "META-INF/versions/$version/$basePath"
    }
}
