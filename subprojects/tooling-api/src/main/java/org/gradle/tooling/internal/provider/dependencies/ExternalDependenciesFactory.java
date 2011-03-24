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

import org.gradle.api.Project;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.tooling.internal.protocol.ExternalDependencyVersion1;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Szczepan Faber, @date: 23.03.11
 */
public class ExternalDependenciesFactory {
    public List<ExternalDependencyVersion1> create(final Project project, Classpath classpath) {
        List<ClasspathEntry> entries = classpath.getEntries();
        List<ExternalDependencyVersion1> dependencies = new LinkedList<ExternalDependencyVersion1>();
        for (ClasspathEntry entry : entries) {
            if (entry instanceof Library) {
                final String path = ((Library) entry).getPath();
                dependencies.add(new ExternalDependencyVersion1() {
                    public File getFile() {
                        return project.file(path);
                    }
                });
            }
        }
        return dependencies;
    }
}
