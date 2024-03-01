/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableMap;
import groovy.lang.GroovyObjectSupport;
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.testing.jacoco.tasks.JacocoReportsContainer;

public class AntJacocoReport extends AbstractAntJacocoReport<JacocoReportsContainer> {

    public AntJacocoReport(IsolatedAntBuilder ant) {
        super(ant);
    }

    public void execute(FileCollection classpath, final String projectName,
                        final FileCollection allClassesDirs, final FileCollection allSourcesDirs,
                        final FileCollection executionData, final JacocoReportsContainer reports) {
        configureAntReportTask(classpath, new Action<GroovyObjectSupport>() {
            @Override
            public void execute(GroovyObjectSupport antBuilder) {
                invokeJacocoReport(antBuilder, projectName, allClassesDirs, allSourcesDirs, executionData, reports);
            }
        });
    }

    @Override
    protected void configureReport(GroovyObjectSupport antBuilder, JacocoReportsContainer reports) {
        if (reports.getHtml().getRequired().get()) {
            antBuilder.invokeMethod("html", new Object[]{
                ImmutableMap.<String, Object>of("destdir", reports.getHtml().getOutputLocation().getAsFile().get())
            });
        }
        if (reports.getXml().getRequired().get()) {
            antBuilder.invokeMethod("xml", new Object[]{
                ImmutableMap.<String, Object>of("destfile", reports.getXml().getOutputLocation().getAsFile().get())
            });
        }
        if (reports.getCsv().getRequired().get()) {
            antBuilder.invokeMethod("csv", new Object[]{
                ImmutableMap.<String, Object>of("destfile", reports.getCsv().getOutputLocation().getAsFile().get())
            });
        }
    }
}
