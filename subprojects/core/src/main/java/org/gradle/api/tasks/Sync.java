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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.file.copy.DestinationRootCopySpec;
import org.gradle.api.internal.file.copy.FileCopyAction;
import org.gradle.api.internal.file.copy.SyncCopyActionDecorator;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
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
 * @since 0.9
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class Sync extends AbstractCopyTask {

    private final PatternFilterable preserveInDestination = new PatternSet();

    @SuppressWarnings("this-escape")
    public Sync() {
        getSyncWhenSourceIsEmpty().convention(false);
    }

    /**
     * Whether this task runs when its source contains no files and no directories.
     *
     * <p>
     * When this is {@code false}, which is the default, a task with an empty source does not run and its
     * destination directory is not synchronized. What is left in the destination then depends on where it is:
     * a destination inside the build directory is cleaned up, while one outside it keeps the files the source
     * no longer contains.
     *
     * <p>
     * When this is {@code true}, the task always runs its copy action, so an empty source empties the
     * destination. Note that this task always deletes the entire contents of its destination directory, not
     * only the files it copied there, except for anything matched by {@link #preserve(Action)}; enabling this
     * extends that to a source that is empty, including one that is empty by mistake.
     *
     * @return whether this task runs when its source is empty
     * @since 9.8.0
     */
    @Incubating
    @Input
    public abstract Property<Boolean> getSyncWhenSourceIsEmpty();

    @Override
    boolean shouldSkipWhenSourceIsEmpty() {
        return !getSyncWhenSourceIsEmpty().get();
    }

    @Override
    protected CopyAction createCopyAction() {
        File destinationDir = getDestinationDir();
        if (destinationDir == null) {
            throw new InvalidUserDataException("No copy destination directory has been specified, use 'into' to specify a target directory.");
        }
        return new SyncCopyActionDecorator(
            destinationDir,
            new FileCopyAction(getFileLookup().getFileResolver(destinationDir)),
            preserveInDestination,
            getDeleter(),
            getDirectoryFileTreeFactory()
        );
    }

    @Override
    protected CopySpecInternal createRootSpec() {
        return getObjectFactory().newInstance(DestinationRootCopySpec.class, super.createRootSpec());
    }

    @Override
    @NotToBeReplacedByLazyProperty(because = "Read-only nested like property")
    public DestinationRootCopySpec getRootSpec() {
        return (DestinationRootCopySpec) super.getRootSpec();
    }

    /**
     * The directory to copy files into.
     * <p>
     * Setting this property is equivalent to calling {@link #into(Object)} on this task, and reading it reflects
     * the destination configured through {@link #into(Object)} or {@link #setDestinationDir(File)}.
     *
     * @return the destination directory property
     * @since 9.8.0
     */
    @Incubating
    @OutputDirectory
    public DirectoryProperty getDestinationDirectory() {
        return getRootSpec().getDestinationDirectory();
    }

    /**
     * Returns the directory to copy files into.
     *
     * @return The destination dir.
     * @since 0.9
     */
    @ReplacedBy("destinationDirectory")
    @NotToBeReplacedByLazyProperty(because = "Superseded by the lazy getDestinationDirectory() property", willBeDeprecated = true)
    public File getDestinationDir() {
        return getRootSpec().getDestinationDir();
    }

    /**
     * Sets the directory to copy files into. This is the same as calling {@link #into(Object)} on this task.
     *
     * @param destinationDir The destination directory. Must not be null.
     * @since 0.9
     */
    public void setDestinationDir(File destinationDir) {
        into(destinationDir);
    }

    /**
     * Returns the filter that defines which files to preserve in the destination directory.
     *
     * @return the filter defining the files to preserve
     * @see #getDestinationDir()
     * @since 3.1
     */
    @Internal
    @NotToBeReplacedByLazyProperty(because = "Read-only nested like property")
    public PatternFilterable getPreserve() {
        return preserveInDestination;
    }

    /**
     * Configures the filter that defines which files to preserve in the destination directory.
     *
     * @param action Action for configuring the preserve filter
     * @return this
     * @see #getDestinationDir()
     * @since 3.1
     */
    public Sync preserve(Action<? super PatternFilterable> action) {
        action.execute(preserveInDestination);
        return this;
    }

    @Inject
    protected abstract Deleter getDeleter();
}
