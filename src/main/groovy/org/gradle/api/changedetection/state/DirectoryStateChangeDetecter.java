package org.gradle.api.changedetection.state;

import org.gradle.api.changedetection.ChangeProcessor;

/**
 * @author Tom Eyckmans
 */
public interface DirectoryStateChangeDetecter {
    void detectChanges(ChangeProcessor changeProcessor);
}
