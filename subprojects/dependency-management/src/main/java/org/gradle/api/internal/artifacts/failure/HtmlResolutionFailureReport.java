/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.failure;

import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Writes an HTML report describing resolution failures to a file.
 *
 * @since 7.5
 */
public final class HtmlResolutionFailureReport {
    private static final String HTML_TEMPLATE_FILE_NAME = "resolution-failure-report.html.template";
    private static final String LOGO_FILE_NAME = "gradle-logo.png";
    private static final String CSS_FILE_NAME = "resolution-failure-styles.css";

    private final File reportFile;

    public HtmlResolutionFailureReport(File reportFile) {
        this.reportFile = reportFile;
    }

    public void writeReport(List<Throwable> errors) {
        getReportDir().mkdirs();

        writeReportHtmlFile(errors);
        writeReportResourceFiles();
    }

    private void writeReportHtmlFile(List<Throwable> errors) {
        String body = buildBody(errors);
        String template = loadTemplate();
        String report = template.replace("$$REPORT_BODY$$", body);

        try (FileWriter writer = new FileWriter(reportFile)) {
            writer.write(report);
        } catch (IOException e) {
            throw new GradleException("Error writing resolution failure report contents", e);
        }
    }

    private String buildBody(List<Throwable> errors) {
        try (final StringWriter writer = new StringWriter()) {
            final HtmlResolutionFailureReportBodyRenderer bodyRenderer = new HtmlResolutionFailureReportBodyRenderer();
            bodyRenderer.render(errors, writer);
            return writer.toString();
        } catch (IOException e) {
            throw new GradleException("Error writing resolution failure report contents", e);
        }
    }

    private String loadTemplate() {
        try (InputStream in = getClass().getResourceAsStream(HTML_TEMPLATE_FILE_NAME)) {
            return IOUtils.toString(in, Charset.defaultCharset());
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void writeReportResourceFiles() {
        File imgDir = new File(getReportDir(), "img");
        imgDir.mkdirs();
        File logoFile = new File(imgDir, LOGO_FILE_NAME);
        copyResource(LOGO_FILE_NAME, logoFile);

        File cssDir = new File(getReportDir(), "css");
        cssDir.mkdirs();
        File cssFile = new File(cssDir, CSS_FILE_NAME);
        copyResource(CSS_FILE_NAME, cssFile);
    }

    private void copyResource(String resourceFileName, File outputFile) {
        try (InputStream in = getClass().getResourceAsStream(resourceFileName)) {
            Files.copy(in, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private File getReportDir() {
        return reportFile.getParentFile();
    }
}
