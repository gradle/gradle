/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.internal.DefaultTask;

import java.io.IOException;

/**
 * The base class for all project report tasks.
 */
public abstract class AbstractReportTask extends DefaultTask {
    public AbstractReportTask(Project project, String name) {
        super(project, name);
        setDagNeutral(true);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                try {
                    generate();
                } catch (IOException e) {
                    throw new GradleException(e);
                }
            }
        });
    }

    public abstract void generate() throws IOException;
}
