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
import org.gradle.api.Task;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.Configurable;
import org.gradle.util.ConfigureUtil;

import java.util.HashSet;
import java.util.Set;

public abstract class ReportContainerBuilder implements Configurable<ReportContainerBuilder> {

    private final Set<Report> enabled = new HashSet<Report>();
    private final Set<Report> disabled = new HashSet<Report>();
    private final Instantiator instantiator;

    protected ReportContainerBuilder(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public static ReportContainerBuilder forTask(Task task) {
        return new TaskReportContainerBuilder(task);
    }
    
    private static class TaskReportContainerBuilder extends ReportContainerBuilder {
        private Task task;
               
        private TaskReportContainerBuilder(Task task) {
            super(((ProjectInternal)task.getProject()).getServices().get(Instantiator.class));
            this.task = task;
        }

        @Override
        protected <T extends Report> T add(Set<Report> target, Class<T> clazz, Object[] constructionArgs) {
            Object[] constructionArgsPlusTask = new Object[constructionArgs.length + 1];
            
            int i = 0;
            for (Object arg : constructionArgs) {
                constructionArgsPlusTask[i++] = arg;
            }
            constructionArgsPlusTask[i] = task;
            return super.add(target, clazz, constructionArgsPlusTask);
        }
    }

    public <T extends Report> T enabled(Class<T> clazz, Object... constructionArgs) {
        return add(enabled, clazz, constructionArgs);
    }

    public <T extends Report> T disabled(Class<T> clazz, Object... constructionArgs) {
        return add(disabled, clazz, constructionArgs);
    }
    
    protected <T extends Report> T add(Set<Report> target, Class<T> clazz, Object[] constructionArgs) {
        T report = instantiator.newInstance(clazz, constructionArgs);
        target.add(report);
        return report;
    }

    public ReportContainerBuilder configure(Closure cl) {
        return ConfigureUtil.configure(cl, this, false);
    }

    public ReportContainer build(Closure config) {
        configure(config);
        return build();
    }

    public ReportContainer build() {
        Report[] combined = new Report[enabled.size() + disabled.size()];
        
        int i = 0;
        for (Report report : enabled) {
            combined[i++] = report;
        }
        for (Report report : disabled) {
            combined[i++] = report;
        }

        return new ReportContainer(combined) {
            @Override
            protected void configureDefaultEnabled() {
                this.setEnabled(ReportContainerBuilder.this.enabled);
            }
        };
    }
}
