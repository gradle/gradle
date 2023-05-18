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
import org.gradle.api.plugins.quality.CodeNarcReports;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Parameters used to configure a {@link CodeNarcAction}.
 */
public interface CodeNarcActionParameters extends AntWorkParameters {

    ConfigurableFileCollection getCompilationClasspath();

    RegularFileProperty getConfig();

    Property<Integer> getMaxPriority1Violations();

    Property<Integer> getMaxPriority2Violations();

    Property<Integer> getMaxPriority3Violations();

    ListProperty<EnabledReport> getEnabledReports();

    Property<Boolean> getIgnoreFailures();

    ConfigurableFileCollection getSource();

    /**
     * Based off of {@link CodeNarcReports}.
     */
    interface EnabledReport {

        Property<String> getName();

        RegularFileProperty getOutputLocation();
    }
}
