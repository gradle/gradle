/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.wrapper

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.junit.Assert.assertEquals

class DownloadTest {
    Download download
    File testDir
    File downloadFile
    File rootDir
    URI sourceRoot
    File remoteFile
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    @Before public void setUp() {
        download = new Download("gradlew", "aVersion")
        testDir = tmpDir.testDirectory
        rootDir = new File(testDir, 'root')
        downloadFile = new File(rootDir, 'file')
        (remoteFile = new File(testDir, 'remoteFile')).write('sometext')
        sourceRoot = remoteFile.toURI()
    }

    @Test public void testDownload() {
        assert !downloadFile.exists()
        download.download(sourceRoot, downloadFile)
        assert downloadFile.exists()
        assertEquals('sometext', downloadFile.text)
    }
}
