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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.internal.file.Deleter;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * <p>Deletes files or directories. Example:</p>
 * <pre class='autoTested'>
 * task makePretty(type: Delete) {
 *   delete 'uglyFolder', 'uglyFile'
 *   followSymlinks = true
 * }
 * </pre>
 *
 * Be default symlinks will not be followed when deleting files. To change this behavior call
 * {@link Delete#setFollowSymlinks(boolean)} with true. On systems that do not support symlinks,
 * this will have no effect.
 */
@DisableCachingByDefault(because = "Deletion cannot be cached")
public class Delete extends ConventionTask implements DeleteSpec {
    private ConfigurableFileCollection paths = getProject().getObjects().fileCollection();

    private boolean followSymlinks;

    @TaskAction
    protected void clean() throws IOException {
        boolean didWork = false;
        for (File path : paths) {
            didWork |= getDeleter().deleteRecursively(path, followSymlinks);
        }
        setDidWork(didWork);
    }

    /**
     * Returns the resolved set of files which will be deleted by this task.
     *
     * @return The files. Never returns null.
     */
    @Destroys
    public FileCollection getTargetFiles() {
        return paths;
    }

    /**
     * Returns the set of files which will be deleted by this task.
     *
     * @return The files. Never returns null.
     */
    @Internal
    public Set<Object> getDelete() {
        return paths.getFrom();
    }

    /**
     * Sets the files to be deleted by this task.
     *
     * @param targets A set of any type of object accepted by {@link org.gradle.api.Project#files(Object...)}
     * @since 4.0
     */
    public void setDelete(Set<Object> targets) {
        this.paths.setFrom(targets);
    }

    /**
     * Sets the files to be deleted by this task.
     *
     * @param target Any type of object accepted by {@link org.gradle.api.Project#files(Object...)}
     */
    public void setDelete(Object target) {
        this.paths.setFrom(target);
    }

    /**
     * Returns if symlinks should be followed when doing a delete.
     *
     * @return true if symlinks will be followed.
     */
    @Input
    public boolean isFollowSymlinks() {
        return followSymlinks;
    }

    /**
     * Set if symlinks should be followed. If the platform doesn't support symlinks, then this will have no effect.
     *
     * @param followSymlinks if symlinks should be followed.
     */
    @Override
    public void setFollowSymlinks(boolean followSymlinks) {
        this.followSymlinks = followSymlinks;
    }

    /**
     * Adds some files to be deleted by this task. The given targets are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param targets Any type of object accepted by {@link org.gradle.api.Project#files(Object...)}
     */
    @Override
    public Delete delete(Object... targets) {
        paths.from(targets);
        return this;
    }

    @Inject
    protected Deleter getDeleter() {
        throw new UnsupportedOperationException("Decorator injects implementation");
    }
}
