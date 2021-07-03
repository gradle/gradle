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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.util.TreeSet;

/**
 * The base class for all Project based project report tasks.
 *
 * @since 6.8
 */
@Incubating
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
public abstract class ProjectBasedReportTask extends ConventionReportTask {

    protected abstract void generate(Project project) throws IOException;

    @TaskAction
    public void generate() {
        reportGenerator().generateReport(
            new TreeSet<>(getProjects()),
            project -> {
                generate(project);
                logClickableOutputFileUrl();
            }
        );
    }
}
