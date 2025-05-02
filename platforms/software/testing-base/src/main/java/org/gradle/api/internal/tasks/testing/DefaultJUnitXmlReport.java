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

import org.gradle.api.Describable;
import org.gradle.api.provider.Property;
import org.gradle.api.reporting.internal.SingleDirectoryReport;
import org.gradle.api.tasks.testing.JUnitXmlReport;

public abstract class DefaultJUnitXmlReport extends SingleDirectoryReport implements JUnitXmlReport {

    private boolean outputPerTestCase;

    public DefaultJUnitXmlReport(String name, Describable owner) {
        super(name, owner, null);
        this.getMergeReruns().convention(false);
        this.getIncludeSystemOutLog().convention(true);
        this.getIncludeSystemErrLog().convention(true);
    }

    @Override
    public boolean isOutputPerTestCase() {
        return outputPerTestCase;
    }

    @Override
    public void setOutputPerTestCase(boolean outputPerTestCase) {
        this.outputPerTestCase = outputPerTestCase;
    }

    @Override
    public abstract Property<Boolean> getMergeReruns();

    @Override
    public abstract Property<Boolean> getIncludeSystemOutLog();

    @Override
    public abstract Property<Boolean> getIncludeSystemErrLog();
}
