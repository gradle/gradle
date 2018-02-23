/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality;

import com.google.common.collect.Sets;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.resources.TextResource;

import java.io.File;
import java.util.Set;

/**
 * Configuration options for the CodeNarc plugin.
 *
 * @see CodeNarc
 */
public class CodeNarcExtension extends CodeQualityExtension {

    private static final Set<String> REPORT_FORMATS = Sets.newHashSet("xml", "html", "console", "text");

    private final Project project;

    private TextResource config;
    private int maxPriority1Violations;
    private int maxPriority2Violations;
    private int maxPriority3Violations;
    private String reportFormat;

    public CodeNarcExtension(Project project) {
        this.project = project;
    }

    /**
     * The CodeNarc configuration to use. Replaces the {@code configFile} property.
     *
     * @since 2.2
     */
    @Incubating
    public TextResource getConfig() {
        return config;
    }

    /**
     * The CodeNarc configuration to use. Replaces the {@code configFile} property.
     *
     * @since 2.2
     */
    @Incubating
    public void setConfig(TextResource config) {
        this.config = config;
    }

    /**
     * The CodeNarc configuration file to use.
     */
    public File getConfigFile() {
        return getConfig().asFile();
    }

    /**
     * The CodeNarc configuration file to use.
     */
    public void setConfigFile(File file) {
        setConfig(project.getResources().getText().fromFile(file));
    }

    /**
     * The maximum number of priority 1 violations allowed before failing the build.
     */
    public int getMaxPriority1Violations() {
        return maxPriority1Violations;
    }

    /**
     * The maximum number of priority 1 violations allowed before failing the build.
     */
    public void setMaxPriority1Violations(int maxPriority1Violations) {
        this.maxPriority1Violations = maxPriority1Violations;
    }

    /**
     * The maximum number of priority 2 violations allowed before failing the build.
     */
    public int getMaxPriority2Violations() {
        return maxPriority2Violations;
    }

    /**
     * The maximum number of priority 2 violations allowed before failing the build.
     */
    public void setMaxPriority2Violations(int maxPriority2Violations) {
        this.maxPriority2Violations = maxPriority2Violations;
    }

    /**
     * The maximum number of priority 3 violations allowed before failing the build.
     */
    public int getMaxPriority3Violations() {
        return maxPriority3Violations;
    }

    /**
     * The maximum number of priority 3 violations allowed before failing the build.
     */
    public void setMaxPriority3Violations(int maxPriority3Violations) {
        this.maxPriority3Violations = maxPriority3Violations;
    }

    /**
     * The format type of the CodeNarc report. One of <tt>html</tt>, <tt>xml</tt>, <tt>text</tt>, <tt>console</tt>.
     */
    public String getReportFormat() {
        return reportFormat;
    }

    /**
     * The format type of the CodeNarc report. One of <tt>html</tt>, <tt>xml</tt>, <tt>text</tt>, <tt>console</tt>.
     */
    public void setReportFormat(String reportFormat) {
        if (REPORT_FORMATS.contains(reportFormat)) {
            this.reportFormat = reportFormat;
        } else {
            throw new InvalidUserDataException("'" + reportFormat + "' is not a valid codenarc report format");
        }
    }
}
