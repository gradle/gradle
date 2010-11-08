package org.gradle.api.internal.file.copy;

import java.io.File;

/**
 * @author Sargis Harutyunyan
 */
public interface FileChmod {
    boolean chmod(File file, int fileMode);
}
