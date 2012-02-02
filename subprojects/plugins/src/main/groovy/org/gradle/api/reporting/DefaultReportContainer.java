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

package org.gradle.api.reporting;

import groovy.lang.Closure;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.internal.ConfigureDelegate;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.DirectInstantiator;
import org.gradle.api.specs.Spec;
import org.gradle.util.ConfigureUtil;

import java.util.Arrays;
import java.util.SortedMap;

public class DefaultReportContainer<T extends Report> extends DefaultNamedDomainObjectSet<T> implements ReportContainer<T> {

    public static class ImmutableViolationException extends GradleException {
        public ImmutableViolationException() {
            super("DefaultReportContainer objects are immutable");
        }
    }

    private NamedDomainObjectSet<T> enabled;

    public DefaultReportContainer(Class<? extends T> type, T... reports) {
        this(type, Arrays.asList(reports));
    }

    public DefaultReportContainer(Class<? extends T> type, Iterable<T> reports) {
        super(type, new DirectInstantiator(), Report.NAMER);
        for (T report : reports) {
            if (getNamer().determineName(report).equals("enabled")) {
                throw new InvalidUserDataException("Reports cannot with a name of 'enabled' cannot be added to ReportContainers as it's reserved");
            }
            add(report);
        }

        enabled = matching(new Spec<T>() {
            public boolean isSatisfiedBy(T element) {
                return element.isEnabled();
            }
        });

        beforeChange(new Runnable() {
            public void run() {
                throw new ImmutableViolationException();
            }
        });
    }

    public NamedDomainObjectSet<T> getEnabled() {
        return enabled;
    }

    public ReportContainer<T> configure(Closure cl) {
        ConfigureUtil.configure(cl, new ConfigureDelegate(cl.getOwner(), this), false);
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
}
