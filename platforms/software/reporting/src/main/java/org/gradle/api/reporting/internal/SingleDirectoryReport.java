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
import org.gradle.internal.Describables;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;

public abstract class SingleDirectoryReport extends SimpleReport implements DirectoryReport {

    @Nullable
    private final String relativeEntryPath;

    @Inject
    public SingleDirectoryReport(String name, Describable owner, @Nullable String relativeEntryPath) {
        super(name, Describables.of(name, "report for", owner), OutputType.DIRECTORY);
        this.relativeEntryPath = relativeEntryPath;
        getOutputLocation().convention(getProjectLayout().dir(new DefaultProvider<>(() -> {
            return (File) ((IConventionAware) SingleDirectoryReport.this).getConventionMapping().getConventionValue(null, "destination", false);
        })));
        getRequired().convention(false);
    }

    @Inject
    protected ProjectLayout getProjectLayout() {
        throw new UnsupportedOperationException();
    }

    @Override
    public File getEntryPoint() {
        if (relativeEntryPath == null) {
            return getOutputLocation().getAsFile().get();
        } else {
            return new File(getOutputLocation().getAsFile().getOrNull(), relativeEntryPath);
        }
    }
}
