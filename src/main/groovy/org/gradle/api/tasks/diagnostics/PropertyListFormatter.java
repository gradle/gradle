/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Project;

import java.io.PrintStream;

public class PropertyListFormatter {
    public static final String SEPARATOR = "------------------------------------------------------------";
    private final PrintStream out;

    public PropertyListFormatter() {
        this(System.out);
    }

    public PropertyListFormatter(PrintStream out) {
        this.out = out;
    }

    public void startProject(Project project) {
        out.println();
        out.println(SEPARATOR);
        out.println(String.format("Project %s", project.getPath()));
        out.println(SEPARATOR);
    }

    public void completeProject(Project project) {
    }

    public void complete() {
    }

    public void addProperty(String name, Object value) {
        out.println(String.format("%s: %s", name, value));
    }
}
