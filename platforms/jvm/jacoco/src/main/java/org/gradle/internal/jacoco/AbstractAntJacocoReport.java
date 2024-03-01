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
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;

import java.util.Collections;
import java.util.Map;

public abstract class AbstractAntJacocoReport<T> {

    protected final IsolatedAntBuilder ant;

    public AbstractAntJacocoReport(IsolatedAntBuilder ant) {
        this.ant = ant;
    }

    protected void configureAntReportTask(FileCollection classpath, final Action<GroovyObjectSupport> action) {
        ant.withClasspath(classpath).execute(new Closure<Object>(this, this) {
            @SuppressWarnings("UnusedDeclaration")
            public Object doCall(Object it) {
                GroovyObjectSupport antBuilder = (GroovyObjectSupport) it;
                antBuilder.invokeMethod("taskdef", ImmutableMap.of(
                        "name", "jacocoReport",
                        "classname", "org.jacoco.ant.ReportTask"
                ));
                action.execute(antBuilder);
                return null;
            }
        });
    }

    protected void invokeJacocoReport(final GroovyObjectSupport antBuilder, final String projectName,
                     final FileCollection allClassesDirs, final FileCollection allSourcesDirs,
                     final FileCollection executionData, final T t) {
        final Map<String, Object> emptyArgs = Collections.emptyMap();
        antBuilder.invokeMethod("jacocoReport", new Object[]{Collections.emptyMap(), new Closure<Object>(this, this) {
            @SuppressWarnings("UnusedDeclaration")
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
                configureReport(antBuilder, t);
                return null;
            }
        }});
    }

    protected abstract void configureReport(GroovyObjectSupport antBuilder, T t);
}
