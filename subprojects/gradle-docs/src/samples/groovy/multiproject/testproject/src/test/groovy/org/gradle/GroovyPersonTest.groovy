package org.gradle

import org.junit.Test
import static org.junit.Assert.*

public class GroovyPersonTest {
    
    @Test
    public void testMarkerFile() throws IOException {
        new File(System.getProperty("org.gradle.integtest.buildDir") + "/" + getClass().getSimpleName()).createNewFile();
    }
}