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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.ReportContainer;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.internal.ConfigureUtil;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Map;

/**
 * An immutable container of {@link Report} instances. Reports can be enabled or disabled.
 * <p>
 * The initial set of reports is configured at creation time by a {@link ReportGenerator}.
 *
 * @param <T> The type of report held by this container.
 */
public class DefaultReportContainer<T extends Report> extends DefaultNamedDomainObjectSet<T> implements ReportContainer<T> {

    /**
     * The set of all enabled reports.
     */
    private final NamedDomainObjectSet<T> enabled;

    /**
     * Create a new report container.
     *
     * @param objectFactory The object factory used for instantiation.
     * @param type The type of report held by this container.
     * @param reportGenerator The generator used to create the initial set of reports.
     *
     * @return A new report container.
     *
     * @param <T> The type of report held by this container.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Report> DefaultReportContainer<T> create(
        ObjectFactory objectFactory,
        Class<? extends T> type,
        ReportGenerator<T> reportGenerator
    ) {
        return objectFactory.newInstance(DefaultReportContainer.class, type, reportGenerator);
    }

    /**
     * Use {@link #create(ObjectFactory, Class, ReportGenerator)}.
     */
    @Inject
    public DefaultReportContainer(
        Class<? extends T> type,
        ReportGenerator<T> reportGenerator,
        InstantiatorFactory instantiatorFactory,
        ServiceRegistry servicesToInject,
        CollectionCallbackActionDecorator callbackActionDecorator
    ) {
        super(type, instantiatorFactory.decorateLenient(servicesToInject), Report.NAMER, callbackActionDecorator);
        this.addAll(reportGenerator.generateReports(new DefaultReportFactory<>(getInstantiator())));
        beforeCollectionChanges(SerializableLambdas.action(arg -> {
            throw new ImmutableViolationException();
        }));

        this.enabled = matching(SerializableLambdas.spec(element -> element.getRequired().get()));
    }

    @Override
    public NamedDomainObjectSet<T> getEnabled() {
        return enabled;
    }

    @Override
    public Map<String, T> getEnabledReports() {
        return getEnabled().getAsMap();
    }

    @Override
    public ReportContainer<T> configure(Closure cl) {
        ConfigureUtil.configureSelf(cl, this);
        return this;
    }

    /**
     * Generates the initial set of reports for this container.
     */
    public interface ReportGenerator<T extends Report> {
        Collection<T> generateReports(ReportFactory<T> factory);
    }

    /**
     * Instantiates reports.
     */
    public interface ReportFactory<T extends Report> {
        <N extends T> N instantiateReport(Class<N> clazz, Object... constructionArgs);
    }

    static class DefaultReportFactory<T extends Report> implements ReportFactory<T> {

        private final Instantiator instantiator;

        public DefaultReportFactory(Instantiator instantiator) {
            this.instantiator = instantiator;
        }

        @Override
        public <N extends T> N instantiateReport(Class<N> clazz, Object... constructionArgs) {
            N report = instantiator.newInstance(clazz, constructionArgs);
            String name = report.getName();
            if (name.equals("enabled")) {
                throw new InvalidUserDataException("Reports that are part of a ReportContainer cannot be named 'enabled'");
            }
            return report;
        }
    }
}
