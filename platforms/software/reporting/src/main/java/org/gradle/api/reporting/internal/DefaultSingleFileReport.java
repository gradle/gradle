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

package org.gradle.api.reporting.internal;

import org.gradle.api.Describable;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.internal.Describables;

import javax.inject.Inject;
import java.io.File;

public abstract class DefaultSingleFileReport extends SimpleReport implements SingleFileReport {

    @Inject
    public DefaultSingleFileReport(String name, Describable owner) {
        super(name, Describables.of(name, "report for", owner), OutputType.FILE);
        // This is for backwards compatibility for plugins that attach a convention mapping to the replaced property
        // TODO - this wiring should happen automatically (and be deprecated too)
        getOutputLocation().convention(getProjectLayout().file(new DefaultProvider<>(() -> {
            return (File) ((IConventionAware) DefaultSingleFileReport.this).getConventionMapping().getConventionValue(null, "destination", false);
        })));
        getRequired().convention(false);
    }

    @Inject
    protected ProjectLayout getProjectLayout() {
        throw new UnsupportedOperationException();
    }
}
