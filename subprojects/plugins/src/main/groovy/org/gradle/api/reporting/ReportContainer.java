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

import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.DirectInstantiator;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.Collection;
import java.util.Set;

public class ReportContainer extends AbstractNamedDomainObjectContainer<Report> {

    public class ImmutableViolationException extends GradleException {
        public ImmutableViolationException() {
            super("ReportContainer objects are immutable");
        }
    }

    DomainObjectSet<Report> enabled = new DefaultDomainObjectSet<Report>(Report.class);
            
    public ReportContainer(Report... reports) {
        super(Report.class, new DirectInstantiator(), Report.NAMER);
        for (Report report : reports) {
            if (getNamer().determineName(report).equals("enabled")) {
                throw new InvalidUserDataException("Reports cannot with a name of 'enabled' cannot be added to ReportContainers as it's reserved");
            }
            add(report);
        }

        enabled.whenObjectAdded(new Action<Report>() {
            public void execute(Report report) {
                if (!contains(report)) {
                    throw new InvalidUserDataException("Cannot enable report " + report + " as part of report container as it is not a member of this container (members: " + GUtil.join(ReportContainer.this, ", ") + ")");
                }
            }
        });

        configureDefaultEnabled();

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
    
    public Set<Report> getEnabled() {
        return enabled;
    }

    public void enabled(Report... enabledReports) {
        setEnabled(WrapUtil.toSet(enabledReports));   
    }
    
    public void setEnabled(Collection<Report> enabledReports) {
        enabled.retainAll(enabledReports);
        for (Report report : enabledReports) {
            if (!enabled.contains(report)) {
                enabled.add(report);
            }
        }
        
    }
}
