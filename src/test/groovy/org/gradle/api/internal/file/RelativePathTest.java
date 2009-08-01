package org.gradle.api.internal.file;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;

public class RelativePathTest {

    private void assertPathContains(RelativePath path, boolean isFile, String... expectedSegments) {
        String[] actualPaths = path.getSegments();
        assertTrue(Arrays.equals(expectedSegments, actualPaths));
        assertEquals(isFile, path.isFile());
    }


    @Test public void testConstructors() {
        RelativePath path, childPath;
        path = new RelativePath(true, "one");
        assertPathContains(path, true, "one");

        path = new RelativePath(false, "one", "two");
        assertPathContains(path, false, "one", "two");

        childPath = new RelativePath(true, path, "three");
        assertPathContains(childPath, true, "one", "two", "three");
    }
}
