package org.gradle.api.artifacts.indexing;

import java.io.File;

/**
 * @author Tom Eyckmans
 */
public class IndexFileUtil {
//    private final File jarsIndexDirectory;

    

    public File packageIndexFile(File jarFile) {
        return new File(jarFile.getParent(), "." + jarFile.getName() + ".package.index");
    }
}
