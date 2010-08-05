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
package org.gradle.execution;

import org.gradle.api.tasks.diagnostics.DependencyReportTask;

public class DependencyReportBuildExecuter extends BuiltInTaskBuildExecuter<DependencyReportTask> {
    public DependencyReportBuildExecuter(String path) {
        super(path);
    }

    @Override
    protected Class<DependencyReportTask> getTaskType() {
        return DependencyReportTask.class;
    }

    public String getDisplayName() {
        return "dependency list";
    }
}
