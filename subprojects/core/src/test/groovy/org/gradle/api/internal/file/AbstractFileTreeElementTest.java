/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.file;

import org.gradle.api.file.RelativePath;
import org.gradle.util.GFileUtils;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.TestFile;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class AbstractFileTreeElementTest {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();

    @Test
    public void canCopyToOutputStream() {
        TestFile src = tmpDir.file("src");
        src.write("content");

        ByteArrayOutputStream outstr = new ByteArrayOutputStream();
        new TestFileTreeElement(src).copyTo(outstr);

        assertThat(new String(outstr.toByteArray()), equalTo("content"));
    }

    @Test
    public void canCopyToFile() {
        TestFile src = tmpDir.file("src");
        src.write("content");
        TestFile dest = tmpDir.file("dir/dest");

        new TestFileTreeElement(src).copyTo(dest);

        dest.assertIsFile();
        assertThat(dest.getText(), equalTo("content"));
    }
    
    private class TestFileTreeElement extends AbstractFileTreeElement {
        private final TestFile file;

        public TestFileTreeElement(TestFile file) {
            this.file = file;
        }

        public String getDisplayName() {
            return "display name";
        }

        public File getFile() {
            return file;
        }

        public long getLastModified() {
            return file.lastModified();
        }

        public boolean isDirectory() {
            return file.isDirectory();
        }

        public long getSize() {
            return file.length();
        }

        public RelativePath getRelativePath() {
            throw new UnsupportedOperationException();
        }

        public InputStream open() {
            return GFileUtils.openInputStream(file);
        }
    }
}
