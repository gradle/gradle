/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.migration.reporting.internal;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.plugins.migration.model.compare.BuildComparisonResult;
import org.gradle.api.plugins.migration.model.render.internal.BuildComparisonResultRenderer;
import org.gradle.api.reporting.internal.TaskGeneratedSingleFileReport;

import java.io.*;
import java.nio.charset.Charset;

public class BuildComparisonResultRendererReport extends TaskGeneratedSingleFileReport {

    private final BuildComparisonResultRenderer<Writer> renderer;

    public BuildComparisonResultRendererReport(String name, Task task, BuildComparisonResultRenderer<Writer> renderer) {
        super(name, task);
        this.renderer = renderer;
    }

    public void render(BuildComparisonResult result) {
        File destination = getDestination();
        if (destination == null) {
            return;
        }

        OutputStream outputStream;
        Writer writer;

        try {
           outputStream = FileUtils.openOutputStream(destination);
           writer = new OutputStreamWriter(outputStream, Charset.defaultCharset());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try {
            renderer.render(result, writer);
        } finally {
            IOUtils.closeQuietly(writer);
            IOUtils.closeQuietly(outputStream);
        }
    }
}
