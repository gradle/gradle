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

import org.gradle.api.Project;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.internal.FileUtils;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.util.Path;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultProjectDescriptor implements ProjectDescriptor, ProjectIdentifier {
    private String name;
    private final PathToFileResolver fileResolver;
    private File dir;
    private DefaultProjectDescriptor parent;
    private Set<ProjectDescriptor> children = new LinkedHashSet<ProjectDescriptor>();
    private ProjectDescriptorRegistry projectDescriptorRegistry;
    private Path path;
    private String buildFileName = Project.DEFAULT_BUILD_FILE;

    public DefaultProjectDescriptor(DefaultProjectDescriptor parent, String name, File dir,
                                    ProjectDescriptorRegistry projectDescriptorRegistry, PathToFileResolver fileResolver) {
        this.parent = parent;
        this.name = name;
        this.fileResolver = fileResolver;
        this.dir = FileUtils.canonicalize(dir);
        this.projectDescriptorRegistry = projectDescriptorRegistry;
        this.path = path(name);
        projectDescriptorRegistry.addProject(this);
        if (parent != null) {
            parent.getChildren().add(this);
        }
    }

    private Path path(String name) {
        if (isRootDescriptor()) {
            return path = Path.ROOT;
        } else {
            return parent.absolutePath(name);
        }
    }

    private Path absolutePath(String path) {
        return this.path.child(path);
    }

    private boolean isRootDescriptor() {
        return parent == null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        projectDescriptorRegistry.changeDescriptorPath(path, path(name));
        this.name = name;
    }

    public File getProjectDir() {
        return dir;
    }

    public void setProjectDir(File dir) {
        this.dir = fileResolver.resolve(dir);
    }

    public DefaultProjectDescriptor getParent() {
        return parent;
    }

    public ProjectIdentifier getParentIdentifier() {
        return parent;
    }

    public Set<ProjectDescriptor> getChildren() {
        return children;
    }

    public String getPath() {
        return path.toString();
    }

    void setPath(Path path) {
        this.path = path;
    }

    public String getBuildFileName() {
        return buildFileName;
    }

    public void setBuildFileName(String name) {
        this.buildFileName = name;
    }

    public File getBuildFile() {
        return FileUtils.canonicalize(new File(dir, buildFileName));
    }

    public ProjectDescriptorRegistry getProjectDescriptorRegistry() {
        return projectDescriptorRegistry;
    }

    public void setProjectDescriptorRegistry(ProjectDescriptorRegistry projectDescriptorRegistry) {
        this.projectDescriptorRegistry = projectDescriptorRegistry;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultProjectDescriptor that = (DefaultProjectDescriptor) o;

        return this.getPath().equals(that.getPath());
    }

    public int hashCode() {
        return this.getPath().hashCode();
    }

    @Override
    public String toString() {
        return getPath();
    }
}
