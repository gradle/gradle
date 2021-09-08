/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.plugins.jvm.JvmTestingFramework;
import org.gradle.api.provider.Property;

import java.util.Collections;
import java.util.List;

public abstract class AbstractJvmTestingFramework implements JvmTestingFramework {
    protected final Project project;
    protected final Property<String> version;

    public AbstractJvmTestingFramework(Project project) {
        this.project = project;
        this.version = project.getObjects().property(String.class);
    }

    @Override
    public Property<String> getVersion() {
        return version;
    }

    @Override
    public List<Dependency> getCompileOnlyDependencies() {
        return Collections.emptyList();
    }

    @Override
    public List<Dependency> getImplementationDependencies() {
        return Collections.emptyList();
    }

    @Override
    public List<Dependency> getRuntimeOnlyDependencies() {
        return Collections.emptyList();
    }
}
