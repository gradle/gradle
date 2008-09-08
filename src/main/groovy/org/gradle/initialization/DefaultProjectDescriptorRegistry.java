/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.initialization.IProjectDescriptorRegistry;
import org.gradle.util.PathHelper;

import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
public class DefaultProjectDescriptorRegistry implements IProjectDescriptorRegistry {
    private Map<String, DefaultProjectDescriptor> projectDescriptors = new HashMap<String, DefaultProjectDescriptor>();
    private Map<File, DefaultProjectDescriptor> projectDir2ProjectDescriptor = new HashMap<File, DefaultProjectDescriptor>();

    public void addProjectDescriptor(DefaultProjectDescriptor projectDescriptor) {
        createProjectDirMapping(projectDescriptor, projectDescriptor.getDir());
        projectDescriptors.put(projectDescriptor.getPath(), projectDescriptor);
    }

    private void createProjectDirMapping(DefaultProjectDescriptor projectDescriptor, File projectDir) {
        ProjectDescriptor existingProjectDescriptor4Dir = projectDir2ProjectDescriptor.get(projectDir);
        if (existingProjectDescriptor4Dir != null && !projectDescriptor.equals(existingProjectDescriptor4Dir)) {
            throw new InvalidUserDataException("The ProjectDescriptor dir is already in use. Path=" +
                    projectDescriptor.getPath() + " Dir=" + projectDir);
        }
        try {
            projectDir2ProjectDescriptor.put(projectDir.getCanonicalFile(), projectDescriptor);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public DefaultProjectDescriptor getProjectDescriptor(String path) {
        return projectDescriptors.get(absolutePath(path));
    }

    public DefaultProjectDescriptor getProjectDescriptor(File projectDir) {
        try {
            return projectDir2ProjectDescriptor.get(projectDir.getCanonicalFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String absolutePath(String path) {
        if (PathHelper.isAbsolutePath(path)) {
            return path;
        }
        return Project.PATH_SEPARATOR + path;
    }

    public void changeProjectDir(File oldDir, File newDir) {
        DefaultProjectDescriptor projectDescriptor = getProjectDescriptor(oldDir);
        assert projectDescriptor != null;
        try {
            projectDir2ProjectDescriptor.remove(oldDir.getCanonicalFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        createProjectDirMapping(projectDescriptor, newDir);
    }

    public void changeDescriptorPath(String oldPath, String newPath) {
        DefaultProjectDescriptor projectDescriptor = getProjectDescriptor(oldPath);
        assert projectDescriptor != null;
        projectDescriptors.remove(oldPath);
        projectDescriptor.setPath(newPath);
        projectDescriptors.put(newPath, projectDescriptor);
    }
}
