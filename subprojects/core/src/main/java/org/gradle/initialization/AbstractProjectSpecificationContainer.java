/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.Action;
import org.gradle.api.file.Directory;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.dsl.ProjectSpecification;
import org.gradle.api.initialization.dsl.ProjectSpecificationContainer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.Actions;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;

import static org.gradle.api.initialization.dsl.ProjectSpecification.LOGICAL_PATH_SEPARATOR;

abstract public class AbstractProjectSpecificationContainer implements ProjectSpecificationContainer {
    public static final String PROJECT_MARKER_FILE = "build.gradle.something";

    protected final Settings settings;
    protected final String logicalPathRelativeToParent;
    protected final ProjectSpecificationContainer parent;

    public AbstractProjectSpecificationContainer(Settings settings, String logicalPathRelativeToParent, @Nullable ProjectSpecificationContainer parent) {
        this.settings = settings;
        this.logicalPathRelativeToParent = logicalPathRelativeToParent;
        this.parent = parent;
        getAutoDetect().convention(true);
        getAutoDetectDirs().add(getProjectDir().map(Directory::getAsFile));
    }

    public AbstractProjectSpecificationContainer(Settings settings) {
        this(settings, "", null);
    }

    @Override
    public DefaultProjectSpecification subproject(String logicalPath) {
        return subproject(logicalPath, Actions.doNothing());
    }

    @Override
    public DefaultProjectSpecification subproject(String logicalPathRelativeToParent, Action<? super ProjectSpecification> action) {
        return subproject(logicalPathRelativeToParent, getProjectDir().dir(logicalPathRelativeToParent), action);
    }

    public DefaultProjectSpecification subproject(String logicalPathRelativeToParent, Provider<Directory> dir, Action<? super ProjectSpecification> action) {
        if (logicalPathRelativeToParent.contains(LOGICAL_PATH_SEPARATOR) || logicalPathRelativeToParent.contains(File.separator)) {
            throw new IllegalArgumentException("The logical path '" + logicalPathRelativeToParent + "' should not contain separators.  To create a complex logical path, use nested calls to the 'subproject()' method.");
        }


        DefaultProjectSpecification subproject = getObjectFactory().newInstance(DefaultProjectSpecification.class, settings, logicalPathRelativeToParent, this);

        if (getProjectRegistry().getProject(subproject.getLogicalPath()) != null) {
            throw new IllegalArgumentException("A project with path '" + subproject.getLogicalPath() + "' has already been registered.");
        }

        settings.include(subproject.getLogicalPath());
        subproject.setExplicitProjectDir(dir);
        getProjectSpecificationRegistry().registerProjectSpecification(subproject);

        action.execute(subproject);

        return subproject;
    }

    private DefaultProjectSpecification subproject(File dir) {
        return subproject(dir.getName(), getObjectFactory().directoryProperty().fileValue(dir), Actions.doNothing());
    }

    private static boolean notEqual(Provider<Directory> dir, Provider<Directory> otherDir) {
        return !dir.get().getAsFile().equals(otherDir.get().getAsFile());
    }

    private boolean isAlreadyRegistered(Provider<Directory> dir) {
        return getProjectRegistry().getProject(dir.get().getAsFile()) != null
            || getProjectSpecificationRegistry().getProjectSpecificationByDir(dir.get().getAsFile()).isPresent();
    }

    @Override
    public void setProjectDirRelativePath(String projectRelativePath) {
        setExplicitProjectDir(parent.getProjectDir().dir(projectRelativePath));
    }

    public void setExplicitProjectDir(Provider<Directory> newProjectDir) {
        Provider<Directory> logicalProjectDir = parent.getProjectDir().dir(logicalPathRelativeToParent);

        if (notEqual(logicalProjectDir, newProjectDir) && isAlreadyRegistered(newProjectDir)) {
            String relativePath = getRootDir().get().getAsFile().toPath().relativize(newProjectDir.get().getAsFile().toPath()).toString();
            throw new IllegalArgumentException("A project with directory '" + relativePath + "' has already been registered.");
        }

        getProjectDir().set(newProjectDir);

        ((DefaultProjectDescriptor)settings.project(getLogicalPath())).setProjectDir(getProjectDir());
    }

    public Provider<Directory> getRootDir() {
        if (parent != null) {
            return ((AbstractProjectSpecificationContainer)parent).getRootDir();
        } else {
            return getProjectDir();
        }
    }

    @Override
    public void autoDetectIfConfigured() {
        if (getAutoDetect().get()) {
            getAutoDetectDirs().get().forEach(dir -> {
                    if (dir.exists()) {
                        File[] subdirs = dir.listFiles(File::isDirectory);
                        if (subdirs != null) {
                            Arrays.stream(subdirs)
                                .filter(file -> new File(file, PROJECT_MARKER_FILE).exists())
                                .forEach(file -> {
                                    ProjectSpecificationContainer spec;
                                    if (isAlreadyRegistered(getProjectDir().dir(file.getName()))) {
                                        spec = getProjectSpecificationRegistry().getProjectSpecificationByDir(file).orElse(null);
                                    } else {
                                        spec = subproject(file);
                                    }
                                    if (spec != null) {
                                        spec.autoDetectIfConfigured();
                                    }
                                });
                        }
                    }
                }
            );
        }
    }

    @Override
    public void addAutoDetectDir(String path) {
        getAutoDetectDirs().add(getProjectDir().dir(path).map(Directory::getAsFile));
    }

    @Inject
    abstract protected ObjectFactory getObjectFactory();

    @Inject
    abstract protected ProjectDescriptorRegistry getProjectRegistry();

    @Inject
    abstract protected ProjectSpecificationRegistry getProjectSpecificationRegistry();

    abstract SetProperty<File> getAutoDetectDirs();
}
