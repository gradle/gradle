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
package org.gradle.api.tasks.ide.eclipse;

import org.apache.commons.io.FilenameUtils;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;

import java.util.*;

/**
 * @author Hans Dockter
 */
class EclipseUtil {
    static Set<Project> getDependsOnProjects(List<DefaultProjectDependency> projectDependencies) {
        Set<Project> dependsOnProjects = new HashSet<Project>();
        for (ProjectDependency projectDependency : projectDependencies) {
            dependsOnProjects.add(projectDependency.getDependencyProject());
        }
        return dependsOnProjects;
    }

    static List<String> getSortedStringList(List pathList) {
        List<String> sortedList = new ArrayList<String>();
        for (Object path : pathList) {
            sortedList.add(path.toString());
        }
        Collections.sort(sortedList);
        return sortedList;
    }

    static String relativePath(Project project, Object path) {
        return FilenameUtils.separatorsToUnix(project.relativePath(path));
    }

    static void addFacet(Document document, String facetType, Attribute... attributes) {
        Element root;

        if (document.getRootElement() == null || (root = ((Element) document.selectSingleNode("//faceted-project"))) == null) {
            root = document.addElement("faceted-project");
        }

        Element facet = root.addElement(facetType);

        for (Attribute attribute : attributes) {
            facet.add(attribute);
        }
    }
}
