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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.file.copy.DestinationRootCopySpec;
import org.gradle.api.internal.file.copy.FileCopyAction;
import org.gradle.api.internal.file.copy.SyncCopyActionDecorator;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;

/**
 * Synchronizes the contents of a destination directory with some source directories and files.
 *
 * <p>
 * This task is like the {@link Copy} task, except the destination directory will only contain the files
 * copied. All files that exist in the destination directory will be deleted before copying files, unless
 * a {@link #preserve(Action)} is specified.
 *
 * <p>
 * Examples:
 * <pre class='autoTested'>
 *
 * // Sync can be used like a Copy task
 * // See the Copy documentation for more examples
 * task syncDependencies(type: Sync) {
 *     from 'my/shared/dependencyDir'
 *     into 'build/deps/compile'
 * }
 *
 * // You can preserve output that already exists in the
 * // destination directory. Files matching the preserve
 * // filter will not be deleted.
 * task sync(type: Sync) {
 *     from 'source'
 *     into 'dest'
 *     preserve {
 *         include 'extraDir/**'
 *         include 'dir1/**'
 *         exclude 'dir1/extra.txt'
 *     }
 * }
 * </pre>
 */
public class Sync extends AbstractCopyTask {

    private final PatternFilterable preserveInDestination = new PatternSet();

    @Override
    protected CopyAction createCopyAction() {
        File destinationDir = getDestinationDir();
        if (destinationDir == null) {
            throw new InvalidUserDataException("No copy destination directory has been specified, use 'into' to specify a target directory.");
        }
        return new SyncCopyActionDecorator(destinationDir, new FileCopyAction(getFileLookup().getFileResolver(destinationDir)), preserveInDestination, getDirectoryFileTreeFactory());
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
     * Returns the filter that defines which files to preserve in the destination directory.
     *
     * @return the filter defining the files to preserve
     *
     * @see #getDestinationDir()
     */
    @Internal
    @Incubating
    public PatternFilterable getPreserve() {
        return preserveInDestination;
    }

    /**
     * Configures the filter that defines which files to preserve in the destination directory.
     *
     * @param action Action for configuring the preserve filter
     * @return this
     *
     * @see #getDestinationDir()
     */
    @Incubating
    public Sync preserve(Action<? super PatternFilterable> action) {
        action.execute(preserveInDestination);
        return this;
    }

}
