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

package org.gradle.api.reporting.internal;

import groovy.lang.Closure;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.internal.DelegatingNamedDomainObjectSet;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.ReportContainer;
import org.gradle.api.tasks.Internal;

import java.util.Map;

/**
 * A {@link ReportContainer} which delegates all methods to a provided delegate.
 */
public abstract class DelegatingReportContainer<T extends Report> extends DelegatingNamedDomainObjectSet<T> implements ReportContainer<T> {

    public DelegatingReportContainer(ReportContainer<T> delegate) {
        super(delegate);
    }

    @Internal
    @Override
    protected ReportContainer<T> getDelegate() {
        return (ReportContainer<T>) super.getDelegate();
    }

    @Override
    public NamedDomainObjectSet<T> getEnabled() {
        return getDelegate().getEnabled();
    }

    @Override
    public Map<String, T> getEnabledReports() {
        return getDelegate().getEnabledReports();
    }

    @Override
    public ReportContainer<T> configure(Closure cl) {
        return getDelegate().configure(cl);
    }
}
