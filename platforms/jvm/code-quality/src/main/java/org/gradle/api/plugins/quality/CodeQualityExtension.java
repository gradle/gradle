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

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

import java.util.Collection;

/**
 * Base Code Quality Extension.
 */
public abstract class CodeQualityExtension {

    public CodeQualityExtension() {
        getIgnoreFailures().convention(false);
    }

    /**
     * The version of the code quality tool to be used.
     */
    @ReplacesEagerProperty
    public abstract Property<String> getToolVersion();

    /**
     * The source sets to be analyzed as part of the <code>check</code> and <code>build</code> tasks.
     */
    @ReplacesEagerProperty(originalType = Collection.class)
    public abstract ListProperty<SourceSet> getSourceSets();

    /**
     * Whether to allow the build to continue if there are warnings.
     *
     * Example: ignoreFailures = true
     */
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getIgnoreFailures();

    @Deprecated
    public Property<Boolean> getIsIgnoreFailures() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsIgnoreFailures()", "getIgnoreFailures()");
        return getIgnoreFailures();
    }

    /**
     * The directory where reports will be generated.
     */
    @ReplacesEagerProperty
    public abstract DirectoryProperty getReportsDir();
}
