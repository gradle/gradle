/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.configuration;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.apache.commons.io.IOUtils;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.io.StringWriter;
import java.io.BufferedWriter;

/**
 * @author Hans Dockter
 */
public class ProjectTasksPrettyPrinter {
    public static final String NL = System.getProperty("line.separator");
    public static final String SEPARATOR = "**************************************************" + NL;

    String getPrettyText(Map<Project, Set<Task>> tasks) {
        StringBuffer sb = new StringBuffer();
        for (Project project : tasks.keySet()) {
            sb.append(SEPARATOR);
            sb.append("Project: " + project + NL);
            for (Task task : tasks.get(project)) {
                sb.append("++Task: " + task.getPath() + ": " + task.getDependsOn() + NL);
            }
        }
        return sb.toString();
    }
}
