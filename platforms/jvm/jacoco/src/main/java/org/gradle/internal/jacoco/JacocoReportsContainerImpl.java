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

package org.gradle.internal.jacoco;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Describable;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.reporting.ConfigurableReport;
import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.reporting.internal.DefaultReportContainer;
import org.gradle.api.reporting.internal.DefaultSingleFileReport;
import org.gradle.api.reporting.internal.DelegatingReportContainer;
import org.gradle.api.reporting.internal.SingleDirectoryReport;
import org.gradle.testing.jacoco.tasks.JacocoReportsContainer;

import javax.inject.Inject;

public class JacocoReportsContainerImpl extends DelegatingReportContainer<ConfigurableReport> implements JacocoReportsContainer {

    @Inject
    public JacocoReportsContainerImpl(Describable owner, ObjectFactory objectFactory) {
        super(DefaultReportContainer.create(objectFactory, ConfigurableReport.class, factory -> ImmutableList.of(
            factory.instantiateReport(SingleDirectoryReport.class, "html", owner, "index.html"),
            factory.instantiateReport(DefaultSingleFileReport.class, "xml", owner),
            factory.instantiateReport(DefaultSingleFileReport.class, "csv", owner)
        )));
    }

    @Override
    public DirectoryReport getHtml() {
        return (DirectoryReport) getByName("html");
    }

    @Override
    public SingleFileReport getXml() {
        return (SingleFileReport) getByName("xml");
    }

    @Override
    public SingleFileReport getCsv() {
        return (SingleFileReport) getByName("csv");
    }
}
