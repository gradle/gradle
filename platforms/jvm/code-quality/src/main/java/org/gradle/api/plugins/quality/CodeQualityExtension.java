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
import org.gradle.api.internal.provider.EagerPropertySupport;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.instrumentation.api.annotations.BackedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.LazyProperties;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;

/**
 * Base Code Quality Extension.
 * @since 1.0
 */
public abstract class CodeQualityExtension {

    /**
     * Creates a new {@code CodeQualityExtension}.
     */
    @SuppressWarnings("this-escape")
    public CodeQualityExtension() {
        getLazyProperties().getIgnoreFailures().convention(false);
    }

    /**
     * The lazy properties behind the eager getters and setters of this extension.
     *
     * @since 10.0.0
     */
    @Incubating
    @LazyProperties
    public abstract CodeQualityExtensionLazyProperties getLazyProperties();

    /**
     * The version of the code quality tool to be used.
     * @since 1.0
     */
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.toolVersion")
    public String getToolVersion() {
        return getLazyProperties().getToolVersion().getOrNull();
    }

    /**
     * The version of the code quality tool to be used.
     * @since 1.0
     */
    public void setToolVersion(String toolVersion) {
        EagerPropertySupport.setEager(getLazyProperties().getToolVersion(), toolVersion);
    }

    /**
     * The source sets to be analyzed as part of the <code>check</code> and <code>build</code> tasks.
     * @since 1.0
     */
    @ToBeReplacedByLazyProperty(comment = "Should this be lazy?")
    @BackedByLazyProperty("lazyProperties.sourceSets")
    public Collection<SourceSet> getSourceSets() {
        ListProperty<SourceSet> sourceSets = getLazyProperties().getSourceSets();
        return sourceSets.isPresent() ? EagerPropertySupport.conventionKeepingView(sourceSets) : null;
    }

    /**
     * The source sets to be analyzed as part of the <code>check</code> and <code>build</code> tasks.
     * @since 1.0
     */
    public void setSourceSets(Collection<SourceSet> sourceSets) {
        EagerPropertySupport.setEager(getLazyProperties().getSourceSets(), sourceSets);
    }

    /**
     * Whether to allow the build to continue if there are warnings.
     *
     * Example: ignoreFailures = true
     * @since 1.0
     */
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.ignoreFailures")
    public boolean isIgnoreFailures() {
        return getLazyProperties().getIgnoreFailures().get();
    }

    /**
     * Whether to allow the build to continue if there are warnings.
     *
     * Example: ignoreFailures = true
     * @since 1.0
     */
    public void setIgnoreFailures(boolean ignoreFailures) {
        getLazyProperties().getIgnoreFailures().set(ignoreFailures);
    }

    /**
     * The directory where reports will be generated.
     *
     * @since 9.7.0
     */
    @Incubating
    public abstract DirectoryProperty getReportsDirectory();

    /**
     * The directory where reports will be generated.
     * @since 1.0
     */
    @ReplacedBy("reportsDirectory")
    @NotToBeReplacedByLazyProperty(because = "Bridge for backward compatibility, use getReportsDirectory() instead", willBeDeprecated = true)
    public File getReportsDir() {
        return getReportsDirectory().isPresent() ? getReportsDirectory().get().getAsFile() : null;
    }

    /**
     * Sets the directory where reports will be generated.
     * @since 1.0
     */
    public void setReportsDir(File reportsDir) {
        getReportsDirectory().set(reportsDir);
        getReportsDirectory().convention(getObjectFactory().directoryProperty().fileValue(reportsDir));
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();
}
