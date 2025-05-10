/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality;

import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;

/**
 * Base Code Quality Extension.
 */
public abstract class CodeQualityExtension {
    private String toolVersion;
    private ListProperty<SourceSet> sourceSets;
    private boolean ignoreFailures;
    private Property<File> reportsDir; // TODO (donat) should be DirectoryProperty; Property<File> to keep semantics as close as possible to the original code

    /**
     * The version of the code quality tool to be used.
     */
    @ToBeReplacedByLazyProperty
    public String getToolVersion() {
        return toolVersion;
    }

    /**
     * The version of the code quality tool to be used.
     */
    public void setToolVersion(String toolVersion) {
        this.toolVersion = toolVersion;
    }

    /**
     * The source sets to be analyzed as part of the <code>check</code> and <code>build</code> tasks.
     */
    @ToBeReplacedByLazyProperty(comment = "Should this be lazy?")
    public Collection<SourceSet> getSourceSets() {
        return maybeInitSourceSets().get();
    }

    /**
     * The source sets to be analyzed as part of the <code>check</code> and <code>build</code> tasks.
     */
    public void setSourceSets(Collection<SourceSet> sourceSets) {
        maybeInitSourceSets().set(sourceSets);
    }

    /**
     * Whether to allow the build to continue if there are warnings.
     *
     * Example: ignoreFailures = true
     */
    @ToBeReplacedByLazyProperty
    public boolean isIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * Whether to allow the build to continue if there are warnings.
     *
     * Example: ignoreFailures = true
     */
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    /**
     * The directory where reports will be generated.
     */
    @ToBeReplacedByLazyProperty
    public File getReportsDir() {
        return maybeInitReportsDir().get();
    }

    /**
     * The directory where reports will be generated.
     */
    public void setReportsDir(File reportsDir) {
        maybeInitReportsDir().set(reportsDir);
    }

    /**
     * Visible for internal use only!
     *
     * @return the reports directory property
     * @since 9.0
     */
    @Incubating
    public Property<File> getReportsDirProperty() {
        return maybeInitReportsDir();
    }

    /**
     * Visible for internal use only!
     * @return the source sets property
     * @since 9.0
     */
    @Incubating
    public ListProperty<SourceSet> getSourceSetsProperty() {
        return maybeInitSourceSets();
    }

    private Property<File> maybeInitReportsDir() {
        if (reportsDir == null) {
            reportsDir = getProject().getObjects().property(File.class);
        }
        return reportsDir;
    }

    private ListProperty<SourceSet> maybeInitSourceSets() {
        if (sourceSets == null) {
            sourceSets = getProject().getObjects().listProperty(SourceSet.class);
        }
        return sourceSets;
    }

    @Inject
    public Project getProject() {
        throw new UnsupportedOperationException();
    }
}
