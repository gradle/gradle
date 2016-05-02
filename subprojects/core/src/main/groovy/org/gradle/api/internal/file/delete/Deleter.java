/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.file.delete;

import org.gradle.api.Action;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.UnableToDeleteFileException;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class Deleter {
    private static final Logger LOGGER = LoggerFactory.getLogger(Deleter.class);

    private FileResolver fileResolver;
    private FileSystem fileSystem;

    private static final int DELETE_RETRY_SLEEP_MILLIS = 10;


    public Deleter(FileResolver fileResolver, FileSystem fileSystem) {
        this.fileResolver = fileResolver;
        this.fileSystem = fileSystem;
    }

    public boolean delete(Object... paths) {
        final Object[] innerPaths = paths;
        return delete(new Action<DeleteSpec>() {
            @Override
            public void execute(DeleteSpec deleteSpec) {
                deleteSpec.delete(innerPaths).setFollowSymlinks(false);
            }
        }).getDidWork();
    }

    public WorkResult delete(Action<? super DeleteSpec> action) {
        boolean didWork = false;
        DeleteSpecInternal deleteSpec = new DefaultDeleteSpec();
        action.execute(deleteSpec);
        Object[] paths = deleteSpec.getPaths();
        for (File file : fileResolver.resolveFiles(paths)) {
            if (!file.exists()) {
                continue;
            }
            LOGGER.debug("Deleting {}", file);
            didWork = true;
            doDeleteInternal(file, deleteSpec);
        }
        return new SimpleWorkResult(didWork);
    }

    private void doDeleteInternal(File file, DeleteSpecInternal deleteSpec) {
        if (file.isDirectory() && (deleteSpec.isFollowSymlinks() || !fileSystem.isSymlink(file))) {
            File[] contents = file.listFiles();

            // Something else may have removed it
            if (contents == null) {
                return;
            }

            for (File item : contents) {
                doDeleteInternal(item, deleteSpec);
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
