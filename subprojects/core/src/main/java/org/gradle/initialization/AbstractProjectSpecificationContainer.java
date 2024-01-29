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
import org.gradle.internal.Actions;

import javax.inject.Inject;
import java.io.File;

import static org.gradle.api.initialization.dsl.ProjectSpecification.LOGICAL_PATH_SEPARATOR;

abstract public class AbstractProjectSpecificationContainer implements ProjectSpecificationContainer {
    protected final Settings settings;
    protected final String logicalPathRelativeToParent;
    protected final ProjectSpecificationContainer parent;

    public AbstractProjectSpecificationContainer(Settings settings, String logicalPathRelativeToParent, ProjectSpecificationContainer parent) {
        this.settings = settings;
        this.logicalPathRelativeToParent = logicalPathRelativeToParent;
        this.parent = parent;
        getProjectDir().set(parent.getProjectDir().dir(logicalPathRelativeToParent));
    }

    public AbstractProjectSpecificationContainer(Settings settings, Provider<Directory> dir) {
        this.settings = settings;
        this.logicalPathRelativeToParent = "";
        this.parent = null;
        getProjectDir().set(dir);
    }

    //@Override
    public ProjectSpecification subproject(String logicalPath) {
        return subproject(logicalPath, Actions.doNothing());
    }

    @Override
    public ProjectSpecification subproject(String logicalPathRelativeToParent, Action<? super ProjectSpecification> action) {
        if (logicalPathRelativeToParent.contains(LOGICAL_PATH_SEPARATOR) || logicalPathRelativeToParent.contains(File.separator)) {
            throw new IllegalArgumentException("The logical path '" + logicalPathRelativeToParent + "' should not contain separators.  To create a complex logical path, use nested calls to the 'subproject()' method.");
        }


        ProjectSpecification subproject = getObjectFactory().newInstance(DefaultProjectSpecification.class, settings, logicalPathRelativeToParent, this);

        if (getProjectRegistry().getProject(subproject.getLogicalPath()) != null) {
            throw new IllegalArgumentException("A project with path '" + subproject.getLogicalPath() + "' has already been registered.");
        }

        settings.include(subproject.getLogicalPath());
        subproject.setProjectDirRelativePath(logicalPathRelativeToParent);

        action.execute(subproject);

        return subproject;
    }

    private static boolean notEqual(Provider<Directory> dir, Provider<Directory> otherDir) {
        return !dir.get().getAsFile().equals(otherDir.get().getAsFile());
    }

    private boolean isAlreadyRegistered(Provider<Directory> dir) {
        return getProjectRegistry().getProject(dir.get().getAsFile()) != null;
    }

    @Override
    public void setProjectDirRelativePath(String projectRelativePath) {
        Provider<Directory> logicalProjectDir = parent.getProjectDir().dir(logicalPathRelativeToParent);
        Provider<Directory> newProjectDir = parent.getProjectDir().dir(projectRelativePath);

        if (notEqual(logicalProjectDir, newProjectDir) && isAlreadyRegistered(newProjectDir)) {
            throw new IllegalArgumentException("A project with directory '" + projectRelativePath + "' has already been registered.");
        }

        getProjectDir().set(newProjectDir);

        ((DefaultProjectDescriptor)settings.project(getLogicalPath())).setProjectDir(getProjectDir());
    }

    @Inject
    abstract protected ObjectFactory getObjectFactory();

    @Inject
    abstract protected ProjectDescriptorRegistry getProjectRegistry();
}
