/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.base.internal;

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.file.Directory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.testing.AggregateTestReport;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestingExtension;
import org.gradle.testing.base.plugins.TestingBasePlugin;

import javax.inject.Inject;

public abstract class DefaultTestingExtension implements TestingExtension {

    private final ExtensiblePolymorphicDomainObjectContainer<TestSuite> suites;
    private final AggregateTestReport results;

    @Inject
    public DefaultTestingExtension(
        ReportingExtension reporting
    ) {
        this.suites = getObjectFactory().polymorphicDomainObjectContainer(TestSuite.class);
        this.results = reporting.getReports().create("aggregateTestReport", AggregateTestReport.class);

        Provider<Directory> testReportsDir = reporting.getBaseDirectory().dir(TestingBasePlugin.TESTS_DIR_NAME);
        this.results.getHtmlReportDirectory().set(testReportsDir.map(it -> it.dir("aggregated-results")));
    }

    @Inject
    public abstract ObjectFactory getObjectFactory();

    @Override
    public ExtensiblePolymorphicDomainObjectContainer<TestSuite> getSuites() {
        return suites;
    }

    @Override
    public AggregateTestReport getResults() {
        return results;
    }
}
