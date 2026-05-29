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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

import java.io.File;
import java.util.Collection;

/**
 * Base Code Quality Extension.
 */
public abstract class CodeQualityExtension {

    private String toolVersion;
    private Collection<SourceSet> sourceSets;
    private boolean ignoreFailures;

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
        return sourceSets;
    }

    /**
     * The source sets to be analyzed as part of the <code>check</code> and <code>build</code> tasks.
     */
    public void setSourceSets(Collection<SourceSet> sourceSets) {
        this.sourceSets = sourceSets;
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
     *
     * @since 9.6.0
     */
    @Incubating
    public abstract DirectoryProperty getReportsDirectory();

    /**
     * The directory where reports will be generated.
     */
    @ReplacedBy("reportsDirectory")
    @NotToBeReplacedByLazyProperty(because = "Bridge for backward compatibility, use getReportsDirectory() instead", willBeDeprecated = true)
    public File getReportsDir() {
        return getReportsDirectory().isPresent() ? getReportsDirectory().get().getAsFile() : null;
    }

    /**
     * Sets the directory where reports will be generated.
     */
    public void setReportsDir(File reportsDir) {
        getReportsDirectory().set(reportsDir);
    }
}
