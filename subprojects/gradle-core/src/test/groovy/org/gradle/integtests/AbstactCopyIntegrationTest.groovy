package org.gradle.integtests

import org.junit.Before
import org.apache.commons.io.FileUtils

public abstract class AbstactCopyIntegrationTest extends AbstractIntegrationTest  {
    @Before
    public void setUp() {
        File resourceFile = null;
        try {
            resourceFile = new File(getClass().getResource("copyTestResources/src").toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException('Could not locate copy test resources');
        }

        File testSrc = testFile('src')
        FileUtils.deleteQuietly(testSrc)

        FileUtils.copyDirectory(resourceFile, testSrc)
    }

    void assertFilesExist(String... paths) {
        for (String path: paths) {
            testFile(path).assertExists()
        }
    }

    void assertFilesMissing(String... paths) {
        for (String path: paths) {
            testFile(path).assertDoesNotExist()
        }
    }
}