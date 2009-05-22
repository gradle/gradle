package org.gradle.api.changedetection;

import java.io.File;

/**
 * @author Tom Eyckmans
 */
public interface ChangeProcessor {
    void createdFile(File newFile);

    void changedFile(File changedFile);

    void deletedFile(File removedFile);
}
