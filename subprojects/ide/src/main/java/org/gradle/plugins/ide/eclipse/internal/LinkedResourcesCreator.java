/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.internal;

import com.google.common.base.Function;
import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.Link;
import org.gradle.plugins.ide.eclipse.model.SourceFolder;
import org.gradle.plugins.ide.eclipse.model.internal.SourceFoldersCreator;

import java.io.File;
import java.util.List;
import java.util.Set;

public class LinkedResourcesCreator {
    public Set<Link> links(final Project project) {
        SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
        EclipseClasspath classpath = project.getExtensions().getByType(EclipseModel.class).getClasspath();
        File defaultOutputDir = classpath == null ? project.file(EclipsePluginConstants.DEFAULT_PROJECT_OUTPUT_PATH) : classpath.getDefaultOutputDir();
        List<SourceFolder> sourceFolders = new SourceFoldersCreator().getBasicExternalSourceFolders(sourceSets, new Function<File, String>() {
            @Override
            public String apply(File dir) {
                return project.relativePath(dir);
            }
        }, defaultOutputDir);
        Set<Link> links = Sets.newLinkedHashSetWithExpectedSize(sourceFolders.size());
        for (SourceFolder sourceFolder : sourceFolders) {
            links.add(new Link(sourceFolder.getName(), "2", sourceFolder.getAbsolutePath(), null));
        }
        return links;
    }
}
