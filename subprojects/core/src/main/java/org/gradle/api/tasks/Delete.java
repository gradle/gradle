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

import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.provider.Property;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Set;

import static org.gradle.api.internal.provider.ProviderApiDeprecationLogger.logDeprecation;
import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.GETTER;

/**
 * <p>Deletes files or directories. Example:</p>
 * <pre class='autoTested'>
 * task makePretty(type: Delete) {
 *   delete 'uglyFolder', 'uglyFile'
 *   followSymlinks = true
 * }
 * </pre>
 *
 * Be default symlinks will not be followed when deleting files. To change this behavior, set
 * {@link Delete#getFollowSymlinks()} property to true. On systems that do not support symlinks,
 * this will have no effect.
 */
@DisableCachingByDefault(because = "Deletion cannot be cached")
public abstract class Delete extends ConventionTask implements DeleteSpec {

    @TaskAction
    protected void clean() throws IOException {
        boolean didWork = false;
        boolean followSymlinks = getFollowSymlinks().getOrElse(false);
        for (File path : getTargetFiles().getFiles()) {
            didWork |= getDeleter().deleteRecursively(path, followSymlinks);
        }
        setDidWork(didWork);
    }

    /**
     * Returns the resolved set of files which will be deleted by this task.
     *
     * @return The files. Never returns null.
     * @since 8.10
     */
    @Destroys
    @ReplacesEagerProperty(replacedAccessors = @ReplacedAccessor(value = GETTER, name = "getTargetFiles"))
    public abstract ConfigurableFileCollection getTargetFiles();

    /**
     * Specifies whether or not symbolic links should be followed during deletion.
     *
     * @since 8.10
     **/
    @Override
    @Incubating
    @ReplacesEagerProperty(replacedAccessors = @ReplacedAccessor(value = GETTER, name = "isFollowSymlinks", originalType = boolean.class))
    public abstract Property<Boolean> getFollowSymlinks();

    /**
     * Returns the set of files which will be deleted by this task.
     *
     * @return The files. Never returns null.
     * @deprecated Use {@link #getTargetFiles()} property instead
     */
    @Internal
    @Deprecated
    public Set<Object> getDelete() {
        logDeprecation(Delete.class, "getDelete()", "targetFiles");
        return getTargetFiles().getFrom();
    }

    /**
     * Sets the files to be deleted by this task.
     *
     * @param targets A set of any type of object accepted by {@link org.gradle.api.Project#files(Object...)}
     * @since 4.0
     * @deprecated Use {@link #getTargetFiles()} property instead
     */
    @Deprecated
    public void setDelete(Set<Object> targets) {
        logDeprecation(Delete.class, "setDelete(Set<Object>)", "targetFiles");
        this.getTargetFiles().setFrom(targets);
    }

    /**
     * Sets the files to be deleted by this task.
     *
     * @param target Any type of object accepted by {@link org.gradle.api.Project#files(Object...)}
     * @deprecated Use {@link #getTargetFiles()} property instead
     */
    @Deprecated
    public void setDelete(Object target) {
        logDeprecation(Delete.class, "setDelete(Object)", "targetFiles");
        this.getTargetFiles().setFrom(target);
    }

    /**
     * Adds some files to be deleted by this task. The given targets are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param targets Any type of object accepted by {@link org.gradle.api.Project#files(Object...)}
     */
    @Override
    public Delete delete(Object... targets) {
        getTargetFiles().from(targets);
        return this;
    }

    @Inject
    protected Deleter getDeleter() {
        throw new UnsupportedOperationException("Decorator injects implementation");
    }

}
