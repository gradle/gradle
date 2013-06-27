/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.Task;
import org.gradle.api.reporting.internal.TaskGeneratedSingleDirectoryReport;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.testing.JUnitXmlReport;

public class DefaultJUnitXmlReport extends TaskGeneratedSingleDirectoryReport implements JUnitXmlReport {

    private boolean outputPerTestCase;

    public DefaultJUnitXmlReport(String name, Task task) {
        super(name, task, null);
    }

    @Input
    public boolean isOutputPerTestCase() {
        return outputPerTestCase;
    }

    public void setOutputPerTestCase(boolean outputPerTestCase) {
        this.outputPerTestCase = outputPerTestCase;
    }

}
