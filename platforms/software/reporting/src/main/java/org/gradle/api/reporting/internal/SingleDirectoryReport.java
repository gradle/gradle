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

package org.gradle.api.reporting.internal;

import org.gradle.api.Describable;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.reporting.DirectoryReport;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;

public abstract class SingleDirectoryReport extends SimpleReport implements DirectoryReport {

    @Nullable
    private final String relativeEntryPath;

    /**
     * Creates a single directory report.
     *
     * @param name The name of the report
     * @param owner A {@link Describable} that describes the container that contains and owns this report
     * @param relativeEntryPath The path of the entry point file relative to the report directory, or null if the entry point is the report directory itself,
     *   not necessarily the owner itself, only used for naming purposes
     */
    @Inject
    public SingleDirectoryReport(String name, Describable owner, @Nullable String relativeEntryPath) {
        super(name, owner, OutputType.DIRECTORY);
        this.relativeEntryPath = relativeEntryPath;
        getOutputLocation().convention(getProjectLayout().dir(new DefaultProvider<>(() -> {
            return (File) ((IConventionAware) SingleDirectoryReport.this).getConventionMapping().getConventionValue(null, "destination", false);
        })));
        getRequired().convention(false);
    }

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @Override
    public File getEntryPoint() {
        if (relativeEntryPath == null) {
            return getOutputLocation().getAsFile().get();
        } else {
            return new File(getOutputLocation().getAsFile().getOrNull(), relativeEntryPath);
        }
    }
}
