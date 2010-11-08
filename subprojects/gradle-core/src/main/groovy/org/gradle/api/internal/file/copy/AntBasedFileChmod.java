package org.gradle.api.internal.file.copy;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Chmod;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.util.AntUtil;

import java.io.File;

/**
 * @author Sargis Harutyunyan
 */
public class AntBasedFileChmod implements FileChmod {

    private static Logger logger = Logging.getLogger(AntBasedFileChmod.class);

    public boolean chmod(File file, int fileMode) {
        try {
            Chmod chmod = new Chmod();
            chmod.setTaskName("chmod");
            chmod.setFile(file);
            chmod.setPerm(Integer.toOctalString(fileMode));

            AntUtil.execute(chmod);
            return true;
        } catch (BuildException ex) {
            logger.debug("Failed to execute ant chmod task", ex);
            return false;
        }
    }

}
