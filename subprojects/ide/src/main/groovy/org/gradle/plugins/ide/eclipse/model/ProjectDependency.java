/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model;

import com.google.common.base.Preconditions;
import groovy.util.Node;

/**
 * A classpath entry representing a project dependency.
 */
public class ProjectDependency extends AbstractClasspathEntry {

    private String gradlePath;

    public ProjectDependency(Node node) {
        super(node);
        assertPathIsValid();
    }

    /**
     * Create a dependency on another Eclipse project.
     * @param path The path to the Eclipse project, which is the name of the eclipse project preceded by "/".
     */
    public ProjectDependency(String path) {
        super(path);
        assertPathIsValid();
    }

    /**
     * Create a dependency on another Eclipse project.
     * @deprecated Use {@link #ProjectDependency(String)} instead
     */
    @Deprecated
    public ProjectDependency(String path, String gradlePath) {
        super(path);
        assertPathIsValid();
        this.gradlePath = gradlePath;
    }

    @Deprecated
    public String getGradlePath() {
        return gradlePath;
    }

    @Deprecated
    public void setGradlePath(String gradlePath) {
        this.gradlePath = gradlePath;
    }

    private void assertPathIsValid() {
        Preconditions.checkArgument(path.startsWith("/"));
    }

    @Override
    public String getKind() {
        return "src";
    }

    @Override
    public String toString() {
        return "ProjectDependency" + super.toString();
    }
}
