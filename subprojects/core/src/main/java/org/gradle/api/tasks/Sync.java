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

package org.gradle.api.tasks;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.CopySpec;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.internal.file.copy.DestinationRootCopySpec;
import org.gradle.api.internal.file.copy.FileCopyAction;
import org.gradle.api.internal.file.copy.SyncCopyActionDecorator;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;

/**
 * Synchronises the contents of a destination directory with some source directories and files.
 */
public class Sync extends AbstractCopyTask {

    private final CopySpecInternal preserveInDestination;

    public Sync() {
        Instantiator instantiator = getInstantiator();
        FileResolver fileResolver = getFileResolver();
        preserveInDestination = instantiator.newInstance(DefaultCopySpec.class, fileResolver, instantiator);
    }

    @Override
    protected CopyAction createCopyAction() {
        File destinationDir = getDestinationDir();
        if (destinationDir == null) {
            throw new InvalidUserDataException("No copy destination directory has been specified, use 'into' to specify a target directory.");
        }
        return new SyncCopyActionDecorator(destinationDir, new FileCopyAction(getFileLookup().getFileResolver(destinationDir)), preserveInDestination);
    }

    @Override
    protected CopySpecInternal createRootSpec() {
        Instantiator instantiator = getInstantiator();
        FileResolver fileResolver = getFileResolver();

        return instantiator.newInstance(DestinationRootCopySpec.class, fileResolver, super.createRootSpec());
    }

    @Override
    public DestinationRootCopySpec getRootSpec() {
        return (DestinationRootCopySpec) super.getRootSpec();
    }

    /**
     * Returns the directory to copy files into.
     *
     * @return The destination dir.
     */
    @OutputDirectory
    public File getDestinationDir() {
        return getRootSpec().getDestinationDir();
    }

    /**
     * Sets the directory to copy files into. This is the same as calling {@link #into(Object)} on this task.
     *
     * @param destinationDir The destination directory. Must not be null.
     */
    public void setDestinationDir(File destinationDir) {
        into(destinationDir);
    }

    /**
    * Returns the CopySpec that defines what files to preserve in {@link #getDestinationDir()}
    *
    * @return    the CopySpec for files to preserve
    *
    * @see #getDestinationDir()
    */
    @Internal
    @Incubating
    public CopySpec getPreserve() {
        return preserveInDestination;
    }

    /**
    * Configures the patterns to preserve in the destination directory.
    *
    * @param action
    *
    * @see #getDestinationDir()
    */
    @Incubating
    public void preserve(Action<? super CopySpec> action) {
        action.execute(preserveInDestination);
    }

}
