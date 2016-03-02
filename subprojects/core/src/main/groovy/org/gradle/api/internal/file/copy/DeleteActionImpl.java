/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import org.gradle.api.file.DeleteAction;
import org.gradle.api.file.UnableToDeleteFileException;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class DeleteActionImpl implements DeleteAction {
    private static Logger logger = LoggerFactory.getLogger(DeleteActionImpl.class);
    
    private FileResolver fileResolver;

    private static final int DELETE_RETRY_SLEEP_MILLIS = 10;

    public DeleteActionImpl(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public boolean delete(Object... deletes) {
        boolean didWork = false;
        for (File file : fileResolver.resolveFiles(deletes)) {
            if (!file.exists()) {
                continue;
            }
            logger.debug("Deleting {}", file);
            didWork = true;
            doDelete(file);
        }
        return didWork;
    }

    private void doDelete(File file) {
        if (file.isDirectory()) {
            File[] contents = file.listFiles();

            // Something else may have removed it
            if (contents == null) {
                return;
            }

            for (File item : contents) {
                doDelete(item);
            }
        }

        if (!file.delete() && file.exists()) {
            handleFailedDelete(file);

        }
    }

    private boolean isRunGcOnFailedDelete() {
        return OperatingSystem.current().isWindows();
    }

    private void handleFailedDelete(File file) {
        // This is copied from Ant (see org.apache.tools.ant.util.FileUtils.tryHardToDelete).
        // It mentions that there is a bug in the Windows JDK impls that this is a valid
        // workaround for. I've been unable to find a definitive reference to this bug.
        // The thinking is that if this is good enough for Ant, it's good enough for us.
        if (isRunGcOnFailedDelete()) {
            System.gc();
        }
        try {
            Thread.sleep(DELETE_RETRY_SLEEP_MILLIS);
        } catch (InterruptedException ex) {
            // Ignore Exception
        }

        if (!file.delete() && file.exists()) {
            throw new UnableToDeleteFileException(file);
        }
    }

}
