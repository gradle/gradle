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
import org.gradle.api.Action;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class AntJacocoReport implements Action<AntBuilderDelegate> {

    private final JacocoReportParameters params;

    public AntJacocoReport(JacocoReportParameters params) {
        this.params = params;
    }

    @Override
    public void execute(AntBuilderDelegate antBuilder) {
        antBuilder.invokeMethod("taskdef", ImmutableMap.of(
            "name", "jacocoReport",
            "classname", "org.jacoco.ant.ReportTask"
        ));
        final Map<String, Object> emptyArgs = Collections.emptyMap();
        antBuilder.invokeMethod("jacocoReport", new Object[]{Collections.emptyMap(), new Closure<Object>(this, this) {
            @SuppressWarnings("UnusedDeclaration")
            public Object doCall(Object ignore) {
                antBuilder.invokeMethod("executiondata", new Object[]{emptyArgs, new Closure<Object>(this, this) {
                    public Object doCall(Object ignore) {
                        params.getExecutionData().filter(File::exists).addToAntBuilder(antBuilder, "resources");
                        return Void.class;
                    }
                }});
                Map<String, Object> structureArgs = ImmutableMap.<String, Object>of("name", params.getProjectName().get());
                antBuilder.invokeMethod("structure", new Object[]{structureArgs, new Closure<Object>(this, this) {
                    public Object doCall(Object ignore) {
                        antBuilder.invokeMethod("classfiles", new Object[]{emptyArgs, new Closure<Object>(this, this) {
                            public Object doCall(Object ignore) {
                                params.getAllClassesDirs().filter(File::exists).addToAntBuilder(antBuilder, "resources");
                                return Void.class;
                            }
                        }});
                        final Map<String, Object> sourcefilesArgs;
                        String encoding = params.getEncoding().getOrNull();
                        if (encoding == null) {
                            sourcefilesArgs = emptyArgs;
                        } else {
                            sourcefilesArgs = Collections.singletonMap("encoding", encoding);
                        }
                        antBuilder.invokeMethod("sourcefiles", new Object[]{sourcefilesArgs, new Closure<Object>(this, this) {
                            public Object doCall(Object ignore) {
                                params.getAllSourcesDirs().filter(File::exists).addToAntBuilder(antBuilder, "resources");
                                return Void.class;
                            }
                        }});
                        return Void.class;
                    }
                }});
                if (params.getGenerateHtml().get()) {
                    antBuilder.invokeMethod("html", new Object[]{
                        ImmutableMap.<String, Object>of("destdir", params.getHtmlDestination().getAsFile().get())
                    });
                }
                if (params.getGenerateXml().get()) {
                    antBuilder.invokeMethod("xml", new Object[]{
                        ImmutableMap.<String, Object>of("destfile", params.getXmlDestination().getAsFile().get())
                    });
                }
                if (params.getGenerateCsv().get()) {
                    antBuilder.invokeMethod("csv", new Object[]{
                        ImmutableMap.<String, Object>of("destfile", params.getCsvDestination().getAsFile().get())
                    });
                }
                return Void.class;
            }
        }});
    }
}
