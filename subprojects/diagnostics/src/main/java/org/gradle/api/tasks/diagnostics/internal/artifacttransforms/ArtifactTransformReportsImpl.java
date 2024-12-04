/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.artifacttransforms;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.reporting.ConfigurableReport;
import org.gradle.api.reporting.internal.DefaultReportContainer;
import org.gradle.api.reporting.internal.DelegatingReportContainer;
import org.gradle.api.tasks.diagnostics.artifacttransforms.ArtifactTransformReports;

import javax.inject.Inject;
import java.util.Collections;

/**
 * Default implementation of {@link ArtifactTransformReports} which allows for adding and configuring reports.
 *
 * Class must be non-{@code final}.
 */
public class ArtifactTransformReportsImpl extends DelegatingReportContainer<ConfigurableReport> implements ArtifactTransformReports {
    @Inject
    public ArtifactTransformReportsImpl(ObjectFactory objectFactory) {
        super(DefaultReportContainer.create(objectFactory, ConfigurableReport.class, factory -> Collections.emptyList()));
    }
}
