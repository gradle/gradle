/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultResolvedArtifact implements ResolvedArtifact {
    private ResolvedDependency resolvedDependency;
    private String name;
    private String type;
    private String extension;
    private File file;

    public DefaultResolvedArtifact(String name, String type, String extension, File file) {
        this.name = name;
        this.type = type;
        this.extension = extension;
        this.file = file;
    }

    public ResolvedDependency getResolvedDependency() {
        return resolvedDependency;
    }

    public void setResolvedDependency(ResolvedDependency resolvedDependency) {
        this.resolvedDependency = resolvedDependency;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getExtension() {
        return extension;
    }

    public String getVersion() {
        return getResolvedDependency() == null ? null : getResolvedDependency().getVersion();
    }

    public String getDependencyName() {
        return getResolvedDependency() == null ? null : getResolvedDependency().getName();
    }

    public File getFile() {
        return file;
    }
}
