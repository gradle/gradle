/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.quality.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.quality.PmdReports;
import org.gradle.api.plugins.quality.TargetJdk;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Parameters used to configure a {@link PmdAction}.
 */
public interface PmdActionParameters extends AntWorkParameters {

    ConfigurableFileCollection getPmdClasspath();

    Property<TargetJdk> getTargetJdk();

    ListProperty<String> getRuleSets();

    ConfigurableFileCollection getRuleSetConfigFiles();

    Property<Boolean> getIgnoreFailures();

    Property<Boolean> getConsoleOutput();

    Property<Boolean> getStdOutIsAttachedToTerminal();

    ConfigurableFileCollection getAuxClasspath();

    Property<Integer> getRulesMinimumPriority();

    Property<Integer> getMaxFailures();

    Property<Boolean> getIncrementalAnalysis();

    RegularFileProperty getIncrementalCacheFile();

    Property<Integer> getThreads();

    ConfigurableFileCollection getSource();

    ListProperty<EnabledReport> getEnabledReports();

    /**
     * Based off of {@link PmdReports}.
     */
    interface EnabledReport {

        Property<String> getName();

        RegularFileProperty getOutputLocation();
    }
}
