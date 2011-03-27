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

package org.gradle.tooling.internal.provider.dependencies;

import org.apache.commons.lang.StringUtils;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.ProjectDependency;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectDependencyVersion2;
import org.gradle.tooling.internal.protocol.eclipse.HierarchicalEclipseProjectVersion1;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Szczepan Faber, @date: 24.03.11
 */
public class EclipseProjectDependenciesFactory {
    public List<EclipseProjectDependencyVersion2> create(final Map<String, ? extends HierarchicalEclipseProjectVersion1> projectMapping, Classpath classpath) {
        final LinkedList<EclipseProjectDependencyVersion2> dependencies = new LinkedList<EclipseProjectDependencyVersion2>();

        List<ClasspathEntry> entries = classpath.getEntries();
        for (ClasspathEntry entry : entries) {
            if (entry instanceof ProjectDependency) {
                final ProjectDependency projectDependency = (ProjectDependency) entry;
                final String path = StringUtils.removeStart(projectDependency.getPath(), "/");
                dependencies.add(new EclipseProjectDependencyVersion2() {
                    public HierarchicalEclipseProjectVersion1 getTargetProject() {
                        return projectMapping.get(projectDependency.getGradlePath());
                    }

                    public String getPath() {
                        return path;
                    }
                });
            }
        }
        return dependencies;
    }
}
