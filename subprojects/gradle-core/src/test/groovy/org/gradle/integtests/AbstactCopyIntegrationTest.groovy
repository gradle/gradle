package org.gradle.integtests

import org.apache.commons.io.FileUtils
import org.junit.Before

public class AbstactCopyIntegrationTest extends AbstractIntegrationTest  {
    @Before
    public void setUp() {
        ['src', 'src2'].each {
            File resourceFile;
            try {
                resourceFile = new File(getClass().getResource("copyTestResources/$it").toURI());
            } catch (URISyntaxException e) {
                throw new RuntimeException('Could not locate copy test resources');
            }

            File testSrc = testFile(it)
            FileUtils.deleteQuietly(testSrc)

            FileUtils.copyDirectory(resourceFile, testSrc)
        }
    }
}