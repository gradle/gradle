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

import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.DirectInstantiator;
import org.gradle.api.specs.Spec;

import java.util.Arrays;

public class ReportContainer extends AbstractNamedDomainObjectContainer<Report> {

    private static final Spec<Report> isEnabledSpec = new Spec<Report>() {
        public boolean isSatisfiedBy(Report element) {
            return element.isEnabled();
        }
    };

    public class ImmutableViolationException extends GradleException {
        public ImmutableViolationException() {
            super("ReportContainer objects are immutable");
        }
    }

    private NamedDomainObjectSet<Report> enabled;

    public ReportContainer(Report... reports) {
        this(Arrays.asList(reports));
    }

    public ReportContainer(Iterable<Report> reports) {
        super(Report.class, new DirectInstantiator(), Report.NAMER);
        for (Report report : reports) {
            if (getNamer().determineName(report).equals("enabled")) {
                throw new InvalidUserDataException("Reports cannot with a name of 'enabled' cannot be added to ReportContainers as it's reserved");
            }
            add(report);
        }

        enabled = matching(isEnabledSpec);

        beforeChange(new Runnable() {
            public void run() {
                throw new ImmutableViolationException();
            }
        });
    }

    @Override
    protected Report doCreate(String name) {
        throw new ImmutableViolationException();
    }

    protected void configureDefaultEnabled() {
        enabled.addAll(this);
    }

    public NamedDomainObjectSet<Report> getEnabled() {
        return enabled;
    }

}
