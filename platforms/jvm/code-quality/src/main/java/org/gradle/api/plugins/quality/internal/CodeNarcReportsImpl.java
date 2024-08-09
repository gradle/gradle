/*
 * Copyright 2009 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Describable;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.quality.CodeNarcReports;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.reporting.internal.DefaultReportContainer;
import org.gradle.api.reporting.internal.DefaultSingleFileReport;
import org.gradle.api.reporting.internal.DelegatingReportContainer;

import javax.inject.Inject;

public class CodeNarcReportsImpl extends DelegatingReportContainer<SingleFileReport> implements CodeNarcReports {

    @Inject
    public CodeNarcReportsImpl(Describable owner, ObjectFactory objectFactory) {
        super(DefaultReportContainer.create(objectFactory, SingleFileReport.class, factory -> ImmutableList.of(
            factory.instantiateReport(DefaultSingleFileReport.class, "xml", owner),
            factory.instantiateReport(DefaultSingleFileReport.class, "html", owner),
            factory.instantiateReport(DefaultSingleFileReport.class, "text", owner),
            factory.instantiateReport(DefaultSingleFileReport.class, "console", owner)
        )));
    }

    @Override
    public SingleFileReport getXml() {
        return getByName("xml");
    }

    @Override
    public SingleFileReport getHtml() {
        return getByName("html");
    }

    @Override
    public SingleFileReport getText() {
        return getByName("text");
    }
}
