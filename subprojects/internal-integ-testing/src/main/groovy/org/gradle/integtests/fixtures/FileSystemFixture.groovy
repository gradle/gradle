/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.fixtures

import groovy.transform.SelfType
import org.apache.commons.lang.RandomStringUtils
import org.gradle.internal.os.OperatingSystem

import java.nio.file.Files

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat

@SelfType(AbstractIntegrationSpec)
trait FileSystemFixture {

    private static def potentialLocationsOnLinux = ["/tmp", "/xfs"]

    TestFileSystem createNewFS() {
        def volumeName = RandomStringUtils.randomAlphanumeric(10)
        if (OperatingSystem.current().isLinux()) {
            def root = getLinuxLocation()
            def location = new File(root, volumeName)
            location.mkdirs()
            return new TestFileSystem(root: location, imageFile: null)
        } else if (OperatingSystem.current().isMacOsX()) {
            def imageFile = file("image${volumeName}.sparseimage")
            executeCommand('hdiutil', 'create', '-size', '300M', '-fs', 'APFS', '-type', 'SPARSE', '-volname', volumeName, '-attach', "image${volumeName}")
            return new TestFileSystem(root: new File("/Volumes/$volumeName"), imageFile: imageFile)
        } else {  //TODO: support Windows
            throw new UnsupportedOperationException("Not implemented yet")
        }
    }

    void cleanupFs(TestFileSystem testFileSystem) {
        if (OperatingSystem.current().isLinux()) {
            testFileSystem.root.deleteDir()
        } else if (OperatingSystem.current().isMacOsX()) {
            executeCommand('hdiutil', 'detach', testFileSystem.root.absolutePath)
            testFileSystem.imageFile.delete()
        } else {
            throw new UnsupportedOperationException("Not implemented yet")
        }
    }

    private File getLinuxLocation() {
        def currentFileStore = Files.getFileStore(file(".").toPath().toRealPath())
        for (String location : potentialLocationsOnLinux) {
            File root = new File(location)
            if (root.exists() && root.canWrite() && Files.getFileStore(root.toPath()) != currentFileStore) {
                return root
            }
        }
        throw new IllegalStateException("No suitable location for linux alternative FS found. Tried: $potentialLocationsOnLinux. Please provide one to potentialLocationsOnLinux in ${FileSystemFixture.class.name}")
    }

    private void executeCommand(String... args) {
        def process = args.execute(null, file("."))
        process.consumeProcessOutput(System.out, System.err)
        assertThat(process.waitFor(), equalTo(0))
    }

    static class TestFileSystem {
        File root;
        File imageFile;
    }
}
