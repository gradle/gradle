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

package org.gradle.api.file;

import org.junit.Test;

import java.io.File;

import static org.gradle.util.Matchers.strictlyEqual;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class RelativePathTest {

    private void assertPathContains(RelativePath path, boolean isFile, String... expectedSegments) {
        String[] actualPaths = path.getSegments();
        assertArrayEquals(expectedSegments, actualPaths);
        assertEquals(isFile, path.isFile());
    }

    @Test
    public void testConstructors() {
        RelativePath path;
        path = new RelativePath(true, "one");
        assertPathContains(path, true, "one");

        path = new RelativePath(false, "one", "two");
        assertPathContains(path, false, "one", "two");
    }

    @Test
    public void appendPath() {
        RelativePath childPath = new RelativePath(false, "one", "two").append(new RelativePath(true, "three", "four"));
        assertPathContains(childPath, true, "one", "two", "three", "four");

        childPath = new RelativePath(false, "one", "two").append(new RelativePath(true));
        assertPathContains(childPath, true, "one", "two");

        childPath = new RelativePath(false, "one", "two").plus(new RelativePath(true, "three"));
        assertPathContains(childPath, true, "one", "two", "three");
    }

    @Test
    public void appendNames() {
        RelativePath childPath = new RelativePath(false, "one", "two").append(true, "three", "four");
        assertPathContains(childPath, true, "one", "two", "three", "four");

        childPath = new RelativePath(false, "one", "two").append(true);
        assertPathContains(childPath, true, "one", "two");
    }

    @Test
    public void prependNames() {
        RelativePath childPath = new RelativePath(false, "one", "two").prepend("three", "four");
        assertPathContains(childPath, false, "three", "four", "one", "two");

        childPath = new RelativePath(false, "one", "two").prepend();
        assertPathContains(childPath, false, "one", "two");
    }

    @Test
    public void hasWellBehavedEqualsAndHashCode() {
        assertThat(new RelativePath(true), strictlyEqual(new RelativePath(true)));
        assertThat(new RelativePath(true, "one"), strictlyEqual(new RelativePath(true, "one")));
        assertThat(new RelativePath(false, "one", "two"), strictlyEqual(new RelativePath(false, "one", "two")));

        assertThat(new RelativePath(true, "one"), not(equalTo(new RelativePath(true, "two"))));
        assertThat(new RelativePath(true, "one"), not(equalTo(new RelativePath(true, "one", "two"))));
        assertThat(new RelativePath(true, "one"), not(equalTo(new RelativePath(false, "one"))));
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

    @Test
    public void canReplaceLastName() {
        assertPathContains(new RelativePath(true, "old").replaceLastName("new"), true, "new");
        assertPathContains(new RelativePath(false, "old").replaceLastName("new"), false, "new");
        assertPathContains(new RelativePath(true, "a", "b", "old").replaceLastName("new"), true, "a", "b", "new");
    }

    @Test
    public void testLength() {
        assertEquals(0, RelativePath.parse(true, "").length());
        assertEquals(7, RelativePath.parse(true, "/one/two").length());
    }

    @Test
    public void testExistingCharAt() {
        RelativePath path = RelativePath.parse(true, "/one/two");
        assertEquals('o', path.charAt(0));
        assertEquals('/', path.charAt(3));
        assertEquals('t', path.charAt(4));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testNegativeCharAt() {
        RelativePath path = RelativePath.parse(true, "/one/two");
        path.charAt(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testTooLargeCharAt() {
        RelativePath path = RelativePath.parse(true, "/one/two");
        path.charAt(25);
    }
}
