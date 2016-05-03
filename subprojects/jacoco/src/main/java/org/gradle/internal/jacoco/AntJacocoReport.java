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
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.testing.jacoco.tasks.JacocoReportsContainer;

import java.util.Collections;
import java.util.Map;

public class AntJacocoReport {

    private final IsolatedAntBuilder ant;

    public AntJacocoReport(IsolatedAntBuilder ant) {
        this.ant = ant;
    }

    public void execute(FileCollection classpath, final String projectName,
                        final FileCollection allClassesDirs, final FileCollection allSourcesDirs,
                        final FileCollection executionData,
                        final JacocoReportsContainer reports) {
        ant.withClasspath(classpath).execute(new Closure<Object>(this, this) {
            @SuppressWarnings("UnusedDeclaration")
            public Object doCall(Object it) {
                final GroovyObjectSupport antBuilder = (GroovyObjectSupport) it;
                antBuilder.invokeMethod("taskdef", ImmutableMap.of(
                    "name", "jacocoReport",
                    "classname", "org.jacoco.ant.ReportTask"
                ));
                final Map<String, Object> emptyArgs = Collections.<String, Object>emptyMap();
                antBuilder.invokeMethod("jacocoReport", new Object[]{emptyArgs, new Closure<Object>(this, this) {
                    public Object doCall(Object ignore) {
                        antBuilder.invokeMethod("executiondata", new Object[]{emptyArgs, new Closure<Object>(this, this) {
                            public Object doCall(Object ignore) {
                                executionData.addToAntBuilder(antBuilder, "resources");
                                return null;
                            }
                        }});
                        Map<String, Object> structureArgs = ImmutableMap.<String, Object>of("name", projectName);
                        antBuilder.invokeMethod("structure", new Object[]{structureArgs, new Closure<Object>(this, this) {
                            public Object doCall(Object ignore) {
                                antBuilder.invokeMethod("classfiles", new Object[]{emptyArgs, new Closure<Object>(this, this) {
                                    public Object doCall(Object ignore) {
                                        allClassesDirs.addToAntBuilder(antBuilder, "resources");
                                        return null;
                                    }
                                }});
                                antBuilder.invokeMethod("sourcefiles", new Object[]{emptyArgs, new Closure<Object>(this, this) {
                                    public Object doCall(Object ignore) {
                                        allSourcesDirs.addToAntBuilder(antBuilder, "resources");
                                        return null;
                                    }
                                }});
                                return null;
                            }
                        }});
                        if (reports.getHtml().isEnabled()) {
                            antBuilder.invokeMethod("html", new Object[]{
                                ImmutableMap.<String, Object>of("destdir", reports.getHtml().getDestination())
                            });
                        }
                        if (reports.getXml().isEnabled()) {
                            antBuilder.invokeMethod("xml", new Object[]{
                                ImmutableMap.<String, Object>of("destfile", reports.getXml().getDestination())
                            });
                        }
                        if (reports.getCsv().isEnabled()) {
                            antBuilder.invokeMethod("csv", new Object[]{
                                ImmutableMap.<String, Object>of("destfile", reports.getCsv().getDestination())
                            });
                        }
                        return null;
                    }
                }});
                return null;
            }
        });
    }
}
