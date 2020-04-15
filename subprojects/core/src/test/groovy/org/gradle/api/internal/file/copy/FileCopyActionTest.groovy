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

package org.gradle.api.internal.file.copy

import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.TestFiles
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.file.copy.CopyActionExecuterUtil.visit

class FileCopyActionTest extends Specification {
    private File destDir

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def setup() throws IOException {
        destDir = tmpDir.getTestDirectory().file("dest")
    }

    def plainCopy() {
        FileCopyAction visitor = new FileCopyAction(TestFiles.resolver(destDir))

        expect:
        visit(visitor,
                file(new RelativePath(true, "rootfile.txt"), new File(destDir, "rootfile.txt")),
                file(new RelativePath(true, "subdir", "anotherfile.txt"), new File(destDir, "subdir/anotherfile.txt"))
        )
    }

    private FileCopyDetailsInternal file(final RelativePath relativePath, final File targetFile) {
        final FileCopyDetailsInternal details = Mock(FileCopyDetailsInternal)
        _ * details.relativePath >> relativePath
        1 * details.copyTo(targetFile)
        0 * details._
        return details
    }
}
