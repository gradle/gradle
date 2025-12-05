/*
 * Copyright 2007 the original author or authors.
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

import com.google.common.base.Objects;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.Cast;
import org.gradle.internal.FileUtils;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.initialization.BuildLogicFiles;
import org.gradle.internal.scripts.DefaultScriptFileResolver;
import org.gradle.internal.scripts.ScriptFileResolver;
import org.gradle.internal.scripts.ScriptFileUtil;
import org.gradle.util.Path;
import org.gradle.util.internal.NameValidator;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultProjectDescriptor implements ProjectDescriptorInternal {
    public static final String INVALID_NAME_IN_INCLUDE_HINT = "Set the 'rootProject.name' or adjust the 'include' statement (see "
        + new DocumentationRegistry().getDslRefForProperty(Settings.class, "include(java.lang.String[])") + " for more details).";

    @Deprecated // Do not immediately remove, since it's an old API that could have been used despite being internal
    public static final String BUILD_SCRIPT_BASENAME = BuildLogicFiles.BUILD_FILE_BASENAME;

    private String name;
    private boolean nameExplicitlySet; // project name explicitly specified in the build script (as opposed to derived from the containing folder)
    private final PathToFileResolver fileResolver;
    private final ScriptFileResolver scriptFileResolver;
    private File dir;
    private @Nullable File canonicalDir;
    private final @Nullable ProjectDescriptorInternal parent;
    private final Set<ProjectDescriptorInternal> children = new LinkedHashSet<>();
    private final ProjectDescriptorRegistry projectDescriptorRegistry;
    private Path path;
    private @Nullable String buildFileName;

    public DefaultProjectDescriptor(
        @Nullable ProjectDescriptorInternal parent, String name, File dir,
        ProjectDescriptorRegistry projectDescriptorRegistry, PathToFileResolver fileResolver
    ) {
        this(parent, name, dir, projectDescriptorRegistry, fileResolver, null);
    }

    public DefaultProjectDescriptor(
        @Nullable ProjectDescriptorInternal parent, String name, File dir,
        ProjectDescriptorRegistry projectDescriptorRegistry, PathToFileResolver fileResolver,
        @Nullable ScriptFileResolver scriptFileResolver
    ) {
        this.parent = parent;
        this.name = name;
        this.fileResolver = fileResolver;
        this.dir = dir;
        this.projectDescriptorRegistry = projectDescriptorRegistry;
        this.path = path(name);
        this.scriptFileResolver = scriptFileResolver != null
            ? scriptFileResolver
            : new DefaultScriptFileResolver();

        projectDescriptorRegistry.addProject(this);
        if (parent != null) {
            parent.children().add(this);
        }
    }

    private Path path(String name) {
        if (isRootDescriptor()) {
            return path = Path.ROOT;
        } else {
            return parent.absolutePath(name);
        }
    }

    @Override
    public Path absolutePath(String path) {
        return this.path.child(path);
    }

    private boolean isRootDescriptor() {
        return parent == null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        NameValidator.validate(name, "project name",
            INVALID_NAME_IN_INCLUDE_HINT);
        projectDescriptorRegistry.changeDescriptorPath(path, path(name));
        this.name = name;
        this.nameExplicitlySet = true;
    }

    @Override
    public boolean isExplicitName() {
        return nameExplicitlySet;
    }

    @Override
    public File getProjectDir() {
        if (canonicalDir == null) {
            canonicalDir = fileResolver.resolve(dir);
        }
        return canonicalDir;
    }

    @Override
    public void setProjectDir(File dir) {
        this.canonicalDir = null;
        this.dir = dir;
    }

    @Override
    public @Nullable ProjectDescriptorInternal getParent() {
        return parent;
    }

    @Override
    public Set<ProjectDescriptor> getChildren() {
        return Cast.uncheckedCast(children);
    }

    @Override
    public Set<ProjectDescriptorInternal> children() {
        return children;
    }

    @Override
    public String getPath() {
        return path.toString();
    }

    @Override
    public void setPath(Path path) {
        this.path = path;
    }

    @Override
    public String getBuildFileName() {
        return buildFile().getName();
    }

    @Override
    public void setBuildFileName(String name) {
        this.buildFileName = name;
    }

    @Override
    public File getBuildFile() {
        return FileUtils.normalize(buildFile());
    }

    private File buildFile() {
        if (buildFileName != null) {
            return new File(getProjectDir(), buildFileName);
        }

        return ScriptFileUtil.resolveBuildFile(getProjectDir(), scriptFileResolver);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultProjectDescriptor that = (DefaultProjectDescriptor) o;

        return Objects.equal(this.getParent(), that.getParent())
            && Objects.equal(this.getName(), that.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.getParent(), this.getName());
    }

    @Override
    public String toString() {
        return getPath();
    }

    @Override
    public Path path() {
        return path;
    }
}
