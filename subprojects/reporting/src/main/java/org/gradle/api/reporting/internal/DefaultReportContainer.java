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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.ReportContainer;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Maps.transformValues;
import static com.google.common.collect.Maps.uniqueIndex;

public class DefaultReportContainer<T extends Report> extends DefaultNamedDomainObjectSet<T> implements ReportContainer<T> {

    private static final Function<Report, String> REPORT_NAME = new Function<Report, String>() {
        @Override
        public String apply(Report report) {
            return report.getName();
        }
    };

    private static final Function<Report, File> TO_FILE = new Function<Report, File>() {
        @Override
        public File apply(Report report) {
            return report.getDestination();
        }
    };

    private static final Predicate<Report> IS_DIRECTORY_OUTPUT_TYPE = new Predicate<Report>() {
        @Override
        public boolean apply(Report report) {
            return report.getOutputType() == Report.OutputType.DIRECTORY;
        }
    };

    private static final Action<Void> IMMUTABLE_VIOLATION_EXCEPTION = new Action<Void>() {
        public void execute(Void arg) {
            throw new ImmutableViolationException();
        }
    };


    private NamedDomainObjectSet<T> enabled;

    public DefaultReportContainer(Class<? extends T> type, Instantiator instantiator) {
        super(type, instantiator, Report.NAMER);

        enabled = matching(new Spec<T>() {
            public boolean isSatisfiedBy(T element) {
                return element.isEnabled();
            }
        });

        beforeChange(IMMUTABLE_VIOLATION_EXCEPTION);
    }

    public NamedDomainObjectSet<T> getEnabled() {
        return enabled;
    }

    public ReportContainer<T> configure(Closure cl) {
        ConfigureUtil.configureSelf(cl, this);
        return this;
    }

    public T getFirstEnabled() {
        SortedMap<String, T> map = enabled.getAsMap();
        if (map.isEmpty()) {
            return null;
        } else {
            return map.get(map.firstKey());
        }
    }

    protected <N extends T> N add(Class<N> clazz, Object... constructionArgs) {
        N report = getInstantiator().newInstance(clazz, constructionArgs);

        if (report.getName().equals("enabled")) {
            throw new InvalidUserDataException("Reports that are part of a ReportContainer cannot be named 'enabled'");
        }

        getStore().add(report);
        index();
        return report;
    }

    @Override
    public Map<String, File> getEnabledDirectoryReportDestinations() {
        return transformValues(uniqueIndex(filter(getEnabled(), IS_DIRECTORY_OUTPUT_TYPE), REPORT_NAME), TO_FILE);
    }

    @Override
    public Map<String, File> getEnabledFileReportDestinations() {
        return transformValues(uniqueIndex(filter(getEnabled(), not(IS_DIRECTORY_OUTPUT_TYPE)), REPORT_NAME), TO_FILE);
    }

    @Override
    public SortedSet<String> getEnabledReportNames() {
        return Sets.newTreeSet(Iterables.transform(getEnabled(), REPORT_NAME));
    }
}
