/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.internal.gradle;

import org.gradle.tooling.internal.protocol.InternalGradleProject;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public class DefaultGradleProject extends PartialGradleProject implements InternalGradleProject, Serializable, GradleProjectIdentity {
    private DefaultGradleScript buildScript = new DefaultGradleScript();

    public DefaultGradleProject() {}

    public DefaultGradleProject(String path) {
        super(path);
    }

    @Override
    public DefaultGradleProject setName(String name) {
        super.setName(name);
        return this;
    }

    @Override
    public DefaultGradleProject setPath(String path) {
        super.setPath(path);
        return this;
    }

    @Override
    public DefaultGradleProject setDescription(String description) {
        super.setDescription(description);
        return this;
    }

    @Override
    public DefaultGradleProject setChildren(List<? extends PartialGradleProject> children) {
        super.setChildren(children);
        return this;
    }

    public DefaultGradleScript getBuildScript() {
        return buildScript;
    }

    public File getProjectDirectory() {
        throw new RuntimeException("ProjectVersion3 methods are deprecated.");
    }

    @Override
    public DefaultGradleProject findByPath(String path) {
        return (DefaultGradleProject) super.findByPath(path);
    }
}
