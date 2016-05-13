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
import org.gradle.util.DeprecationLogger;

/**
 * A classpath entry representing a project dependency.
 */
public class ProjectDependency extends AbstractClasspathEntry {

    private static final String DEPRECATED_DECLAREDCONFIGNAME_FIELD = "ProjectDependency.declaredConfigurationName";

    private String gradlePath;

    private String declaredConfigurationName;

    public ProjectDependency(Node node) {
        super(node);
        assertPathIsValid();
    }

    public ProjectDependency(String path, String gradlePath) {
        super(path);
        assertPathIsValid();
        this.gradlePath = gradlePath;
    }

    public String getGradlePath() {
        return gradlePath;
    }

    public void setGradlePath(String gradlePath) {
        this.gradlePath = gradlePath;
    }

    @Deprecated
    public String getDeclaredConfigurationName() {
        DeprecationLogger.nagUserOfDeprecated(DEPRECATED_DECLAREDCONFIGNAME_FIELD);
        return declaredConfigurationName;
    }

    public void setDeclaredConfigurationName(String declaredConfigurationName) {
        DeprecationLogger.nagUserOfDeprecated(DEPRECATED_DECLAREDCONFIGNAME_FIELD);
        this.declaredConfigurationName = declaredConfigurationName;
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
