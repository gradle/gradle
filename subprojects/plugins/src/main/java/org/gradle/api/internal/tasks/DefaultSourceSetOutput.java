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

package org.gradle.api.internal.tasks;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.util.CollectionUtils;
import org.gradle.util.SingleMessageLogger;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;

public class DefaultSourceSetOutput extends CompositeFileCollection implements SourceSetOutput {
    public static final String SINGLE_CLASSES_DIR_DEPRECATION_MESSAGE = "Gradle now uses separate output directories for each JVM language, but this build assumes a single directory for all classes from a source set";
    private final DefaultConfigurableFileCollection outputDirectories;
    private Object resourcesDir;
    private Object classesDir;

    private final DefaultConfigurableFileCollection classesDirs;
    private final DefaultConfigurableFileCollection dirs;
    private final FileResolver fileResolver;

    public DefaultSourceSetOutput(String sourceSetDisplayName, final FileResolver fileResolver, TaskResolver taskResolver) {
        this.fileResolver = fileResolver;
        String displayName = sourceSetDisplayName + " classes";

        this.classesDirs = new DefaultConfigurableFileCollection("classesDirs", fileResolver, taskResolver);
        // TODO: This should be more specific to just the tasks that create the class files?
        classesDirs.builtBy(this);

        this.outputDirectories = new DefaultConfigurableFileCollection(displayName, fileResolver, taskResolver, new Callable() {
            public Object call() throws Exception {
                return classesDirs;
            }
        }, new Callable() {
            public Object call() throws Exception {
                return getResourcesDir();
            }
        });

        this.dirs = new DefaultConfigurableFileCollection("dirs", fileResolver, taskResolver);
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        context.add(outputDirectories);
    }

    @Override
    public String getDisplayName() {
        return outputDirectories.getDisplayName();
    }

    @Override
    public File getClassesDir() {
        if (isLegacyLayout()) {
            return fileResolver.resolve(classesDir);
        }
        SingleMessageLogger.nagUserOfDeprecatedBehaviour(SINGLE_CLASSES_DIR_DEPRECATION_MESSAGE);
        Object firstClassesDir = CollectionUtils.findFirst(classesDirs.getFrom(), Specs.SATISFIES_ALL);
        if (firstClassesDir!=null) {
            return fileResolver.resolve(firstClassesDir);
        }
        return null;
    }

    @Override
    public void setClassesDir(File classesDir) {
        setClassesDir((Object)classesDir);
    }

    @Override
    public void setClassesDir(Object classesDir) {
        SingleMessageLogger.nagUserOfDeprecatedBehaviour(SINGLE_CLASSES_DIR_DEPRECATION_MESSAGE);
        this.classesDir = classesDir;
        this.classesDirs.setFrom(classesDir);
    }

    @Override
    public FileCollection getClassesDirs() {
        return classesDirs;
    }


    /**
     * Adds a new classes directory that compiled classes are assembled into.
     *
     * @param classesDir the classes dir. Should not be null.
     */
    public void addClassesDir(Callable<File> classesDir) {
        classesDirs.from(classesDir);
    }

    @Override
    public boolean isLegacyLayout() {
        return classesDir!=null;
    }

    @Override
    public File getResourcesDir() {
        if (resourcesDir == null) {
            return null;
        }
        return fileResolver.resolve(resourcesDir);
    }

    @Override
    public void setResourcesDir(File resourcesDir) {
        this.resourcesDir = resourcesDir;
    }

    @Override
    public void setResourcesDir(Object resourcesDir) {
       this.resourcesDir = resourcesDir;
    }

    public void builtBy(Object... taskPaths) {
        outputDirectories.builtBy(taskPaths);
    }

    @Override
    public void dir(Object dir) {
        this.dir(Collections.<String, Object>emptyMap(), dir);
    }

    @Override
    public void dir(Map<String, Object> options, Object dir) {
        this.dirs.from(dir);
        this.outputDirectories.from(dir);

        Object builtBy = options.get("builtBy");
        if (builtBy != null) {
            this.builtBy(builtBy);
            this.dirs.builtBy(builtBy);
        }
    }

    @Override
    public FileCollection getDirs() {
        return dirs;
    }
}
