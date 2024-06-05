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

package org.gradle.test.fixtures.file

import org.gradle.internal.os.OperatingSystem
import org.junit.rules.ExternalResource

import java.nio.file.Files

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat

/**
 * Creates a temporary file system for testing purposes.
 *
 * On Linux, it's hard to mount something without superuser privileges, so we assume that an alternative filesystem is already present.
 * On macOS, we create a sparse image file and attach it as a volume.
 * On Windows, we create a virtual disk and mount it. Superuser privileges are required.
 */
class TempFileSystemProvider extends ExternalResource {

    private static def potentialLocationsOnLinux = ["/tmp", "/xfs"]

    private final File testDirectory
    private List<TestFileSystem> created = []

    TempFileSystemProvider(TestDirectoryProvider provider) {
        testDirectory = provider.testDirectory
    }

    @Override
    protected void after() {
        created.each { destroy(it) }
        created.clear()
    }

    TestFileSystem create() {
        def testFileSystem = doCreate()
        created.add(testFileSystem)
        assertThat(Files.getFileStore(testFileSystem.root.toPath()), not(equalTo(Files.getFileStore(testDirectory.toPath().toRealPath()))))

        return testFileSystem
    }

    private TestFileSystem doCreate() {
        def volumeName = UUID.randomUUID().toString()
        if (OperatingSystem.current().isLinux()) {
            def mountPoint = new File(testDirectory, volumeName)
            mountPoint.mkdirs()
            try {
                executeCommand('sudo', 'mount', '-t', 'tmpfs', '-o', 'size=300M', 'tmpfs', mountPoint.absolutePath)
                return new TestFileSystem(root: mountPoint, imageFile: mountPoint)
            } catch (IOException ignored) { // permission errors
                mountPoint.deleteDir()
            }
            def location = new File(getLinuxLocation(), volumeName)
            location.mkdirs()
            return new TestFileSystem(root: location, imageFile: null)
        } else if (OperatingSystem.current().isMacOsX()) {
            def imageFile = new File(testDirectory, "image${volumeName}.sparseimage")
            executeCommand('hdiutil', 'create', '-size', '300M', '-fs', 'APFS', '-type', 'SPARSE', '-volname', volumeName, '-attach', "image${volumeName}")
            return new TestFileSystem(root: new File("/Volumes/$volumeName"), imageFile: imageFile)
        } else if (OperatingSystem.current().isWindows()) {
            def imageFile = new File(testDirectory, "image${volumeName}.vhdx")
            def mountPoint = new File(testDirectory, volumeName)
            mountPoint.mkdirs()
            executeDiskPartScript(vDiskCreationScript(imageFile, mountPoint))
            return new TestFileSystem(root: mountPoint, imageFile: imageFile)
        } else {
            throw new UnsupportedOperationException("Not implemented yet")
        }
    }

    private void destroy(TestFileSystem testFileSystem) {
        if (OperatingSystem.current().isLinux()) {
            if (testFileSystem.imageFile != null) {
                executeCommand('sudo', 'umount', testFileSystem.imageFile.absolutePath)
            }
            testFileSystem.root.deleteDir()
        } else if (OperatingSystem.current().isMacOsX()) {
            executeCommand('hdiutil', 'detach', testFileSystem.root.absolutePath)
            testFileSystem.imageFile.delete()
        } else if (OperatingSystem.current().isWindows()) {
            executeDiskPartScript(vDiskDetachScript(testFileSystem.imageFile))
            testFileSystem.imageFile.delete()
            testFileSystem.root.deleteDir()
        } else {
            throw new UnsupportedOperationException("Not implemented yet")
        }
    }

    private File getLinuxLocation() {
        def currentFileStore = Files.getFileStore(testDirectory.toPath().toRealPath())
        for (String location : potentialLocationsOnLinux) {
            File root = new File(location)
            if (root.exists() && root.canWrite() && Files.getFileStore(root.toPath()) != currentFileStore) {
                return root
            }
        }
        throw new IllegalStateException("No suitable location for linux alternative FS found. Tried: $potentialLocationsOnLinux. Please provide one to potentialLocationsOnLinux in ${this.class.name}")
    }

    private static String vDiskCreationScript(File imagePath, File mountPoint) {
        return """create vdisk file="${imagePath}" maximum=1000 type=expandable
select vdisk file="${imagePath}"
attach vdisk
create partition primary
format fs=ntfs quick
assign mount="${mountPoint}"
"""
    }

    private static String vDiskDetachScript(File imagePath) {
        return """select vdisk file="${imagePath}"
select partition 1
delete volume
detach vdisk
"""
    }

    private String executeDiskPartScript(String scriptContent) {
        def id = UUID.randomUUID().toString()
        File script = new File(testDirectory, "diskpartScript${id}.txt") << scriptContent
        String result = executeCommand('diskpart', '/s', script.absolutePath)
        script.delete()
        return result
    }

    private void executeCommand(String... args) {
        def process = args.execute(null, testDirectory)
        process.consumeProcessOutput(System.out, System.err)
        int exitValue = process.waitFor();
        if (exitValue != 0) {
            throw new IOException("Command exited with non-zero exit code: " + exitValue);
        }
    }

    static class TestFileSystem {
        File root;
        File imageFile;

        TestFile file(String path) {
            return new TestFile(new File(root, path))
        }
    }
}
