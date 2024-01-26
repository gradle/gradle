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
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.dsl.ProjectSpecification;
import org.gradle.api.initialization.dsl.ProjectSpecificationContainer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.Actions;

import javax.inject.Inject;
import java.io.File;

import static org.gradle.api.initialization.dsl.ProjectSpecification.LOGICAL_PATH_SEPARATOR;

abstract public class AbstractProjectSpecificationContainer implements ProjectSpecificationContainer {
    protected final Settings settings;
    protected final File dir;
    protected final String logicalPathRelativeToParent;
    protected final ProjectSpecificationContainer parent;

    public AbstractProjectSpecificationContainer(Settings settings, File dir, String logicalPathRelativeToParent, ProjectSpecificationContainer parent) {
        this.settings = settings;
        this.dir = dir;
        this.logicalPathRelativeToParent = logicalPathRelativeToParent;
        this.parent = parent;
    }

    @Override
    public ProjectSpecification subproject(String logicalPath) {
        return subproject(logicalPath, Actions.doNothing());
    }

    @Override
    public ProjectSpecification subproject(String logicalPathRelativeToParent, Action<? super ProjectSpecification> action) {
        if (logicalPathRelativeToParent.contains(LOGICAL_PATH_SEPARATOR) || logicalPathRelativeToParent.contains(File.separator)) {
            throw new IllegalArgumentException("The logical path '" + logicalPathRelativeToParent + "' should not contain separators.  To create a complex logical path, use nested calls to the 'subproject()' method.");
        }

        ProjectSpecification subproject = getObjectFactory().newInstance(DefaultProjectSpecification.class, settings, new File(dir, logicalPathRelativeToParent), logicalPathRelativeToParent, this);
        settings.include(subproject.getLogicalPath());
        settings.project(subproject.getLogicalPath()).setProjectDir(subproject.getDir());
        action.execute(subproject);
        return subproject;
    }

    @Override
    public File getDir() {
        return dir;
    }

    @Inject
    abstract protected ObjectFactory getObjectFactory();
}
