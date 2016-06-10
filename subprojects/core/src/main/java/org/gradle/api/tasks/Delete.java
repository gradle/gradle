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
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.delete.Deleter;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import javax.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * <p>Deletes files or directories. Example:</p>
 * <pre autoTested=''>
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
public class Delete extends ConventionTask implements DeleteSpec {
    private Set<Object> delete = new LinkedHashSet<Object>();

    private boolean followSymlinks;

    @Inject
    protected FileSystem getFileSystem() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileResolver getFileResolver() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    @TaskAction
    protected void clean() {
        Deleter deleter = new Deleter(getFileResolver(), getFileSystem());
        final boolean innerFollowSymLinks = followSymlinks;
        final Object[] paths = delete.toArray();
        setDidWork(deleter.delete(new Action<DeleteSpec>(){
            @Override
            public void execute(DeleteSpec deleteSpec) {
                deleteSpec.delete(paths).setFollowSymlinks(innerFollowSymLinks);
            }
        }).getDidWork());
    }

    /**
     * Returns the resolved set of files which will be deleted by this task.
     *
     * @return The files. Never returns null.
     */
    @Internal
    public FileCollection getTargetFiles() {
        return getProject().files(delete);
    }

    /**
     * Returns the set of files which will be deleted by this task.
     *
     * @return The files. Never returns null.
     */
    @Internal
    public Set<Object> getDelete() {
        return delete;
    }

    /**
     * Sets the files to be deleted by this task.
     *
     * @param target Any type of object accepted by {@link org.gradle.api.Project#files(Object...)}
     */
    public void setDelete(Object target) {
        delete.clear();
        this.delete.add(target);
    }

    /**
     * Returns if symlinks should be followed when doing a delete.
     *
     * @return true if symlinks will be followed.
     */
    @Incubating
    @Input
    public boolean isFollowSymlinks() {
        return followSymlinks;
    }

    /**
     * Set if symlinks should be followed. If the platform doesn't support symlinks, then this will have no effect.
     *
     * @param followSymlinks if symlinks should be followed.
     */
    @Incubating
    public void setFollowSymlinks(boolean followSymlinks) {
        this.followSymlinks = followSymlinks;
    }

    /**
     * Adds some files to be deleted by this task. The given targets are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param targets Any type of object accepted by {@link org.gradle.api.Project#files(Object...)}
     */
    public Delete delete(Object... targets) {
        for (Object target : targets) {
            this.delete.add(target);
        }
        return this;
    }
}
