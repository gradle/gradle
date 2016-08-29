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
package org.gradle.plugins.ide.internal.tooling.eclipse;

import org.gradle.tooling.internal.protocol.eclipse.DefaultEclipseProjectIdentifier;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public class DefaultEclipseProjectDependency extends DefaultEclipseDependency implements Serializable {
    private final DefaultEclipseProjectIdentifier targetIdentifier;
    private final String path;

    private final DefaultEclipseProject targetProject;

    /**
     * Creates a dependency on an project in a different Gradle build.
     */
    public DefaultEclipseProjectDependency(String path, DefaultEclipseProject targetProject, boolean exported, List<DefaultClasspathAttribute> attributes, List<DefaultAccessRule> accessRules) {
        super(exported, attributes, accessRules);
        this.targetProject = targetProject;
        this.path = path;
        this.targetIdentifier = new DefaultEclipseProjectIdentifier(targetProject.getProjectDirectory());
    }

    /**
     * Creates a dependency on an project in a different Gradle build.
     */
    public DefaultEclipseProjectDependency(String path, File targetProjectDirectory, boolean exported, List<DefaultClasspathAttribute> attributes, List<DefaultAccessRule> accessRules) {
        super(exported, attributes, accessRules);
        this.targetProject = null;
        this.path = path;
        this.targetIdentifier = new DefaultEclipseProjectIdentifier(targetProjectDirectory);
    }

    public DefaultEclipseProject getTargetProject() {
        return targetProject;
    }

    public DefaultEclipseProjectIdentifier getTarget() {
        return targetIdentifier;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "project dependency " + path;
    }
}
