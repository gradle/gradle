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

package org.gradle.api.internal.file;

import org.gradle.api.file.RelativePath;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

import java.io.File;

public class RelativePathTest {

    private void assertPathContains(RelativePath path, boolean isFile, String... expectedSegments) {
        String[] actualPaths = path.getSegments();
        assertArrayEquals(expectedSegments, actualPaths);
        assertEquals(isFile, path.isFile());
    }

    @Test
    public void testConstructors() {
        RelativePath path, childPath;
        path = new RelativePath(true, "one");
        assertPathContains(path, true, "one");

        path = new RelativePath(false, "one", "two");
        assertPathContains(path, false, "one", "two");

        childPath = new RelativePath(true, path, "three");
        assertPathContains(childPath, true, "one", "two", "three");
    }

    @Test
    public void canParsePathIntoRelativePath() {
        RelativePath path;

        path = RelativePath.parse(true, "one");
        assertPathContains(path, true, "one");

        path = RelativePath.parse(true, "one/two");
        assertPathContains(path, true, "one", "two");

        path = RelativePath.parse(true, "one/two/");
        assertPathContains(path, true, "one", "two");

        path = RelativePath.parse(true, String.format("one%stwo%s", File.separator, File.separator));
        assertPathContains(path, true, "one", "two");

        path = RelativePath.parse(false, "");
        assertPathContains(path, false);

        path = RelativePath.parse(false, "/");
        assertPathContains(path, false);

        path = RelativePath.parse(true, "/one");
        assertPathContains(path, true, "one");

        path = RelativePath.parse(true, "/one/two");
        assertPathContains(path, true, "one", "two");
    }

    @Test
    public void canGetParentOfPath() {
        assertThat(new RelativePath(true, "a", "b").getParent(), equalTo(new RelativePath(false, "a")));
        assertThat(new RelativePath(false, "a", "b").getParent(), equalTo(new RelativePath(false, "a")));
        assertThat(new RelativePath(false, "a").getParent(), equalTo(new RelativePath(false)));
        assertThat(new RelativePath(false).getParent(), nullValue());
    }
}
