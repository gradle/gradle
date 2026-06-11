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
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;
import org.gradle.api.plugins.internal.ant.AntWorkAction;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public abstract class AntJacocoReport extends AntWorkAction<JacocoReportParameters> {

    @Override
    protected String getActionName() {
        return "jacoco-report";
    }

    @Override
    public void execute(AntBuilderDelegate antBuilder) {
        JacocoReportParameters params = getParameters();
        antBuilder.taskdef("jacocoReport", "org.jacoco.ant.ReportTask");
        antBuilder.createNode("jacocoReport", Collections.emptyMap(), () -> {
            antBuilder.createNode("executiondata", Collections.emptyMap(), () -> {
                antBuilder.addFiles("resources", params.getExecutionData().filter(File::exists));
            });
            Map<String, Object> structureArgs = ImmutableMap.<String, Object>of("name", params.getProjectName().get());
            antBuilder.createNode("structure", structureArgs, () -> {
                antBuilder.createNode("classfiles", Collections.emptyMap(), () -> {
                    antBuilder.addFiles("resources", params.getAllClassesDirs().filter(File::exists));
                });
                final Map<String, Object> sourcefilesArgs;
                String encoding = params.getEncoding().getOrNull();
                if (encoding == null) {
                    sourcefilesArgs = Collections.emptyMap();
                } else {
                    sourcefilesArgs = Collections.singletonMap("encoding", encoding);
                }
                antBuilder.createNode("sourcefiles", sourcefilesArgs, () -> {
                    antBuilder.addFiles("resources", params.getAllSourcesDirs().filter(File::exists));
                });
            });
            if (params.getGenerateHtml().get()) {
                antBuilder.createNode("html",
                    ImmutableMap.of("destdir", params.getHtmlDestination().getAsFile().get())
                );
            }
            if (params.getGenerateXml().get()) {
                antBuilder.createNode("xml",
                    ImmutableMap.of("destfile", params.getXmlDestination().getAsFile().get())
                );
            }
            if (params.getGenerateCsv().get()) {
                antBuilder.createNode("csv",
                    ImmutableMap.of("destfile", params.getCsvDestination().getAsFile().get())
                );
            }
        });
    }

}
