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
package org.gradle.api.plugins.quality;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.*;

import java.io.File;

/**
 * Runs CodeNarc against some source files.
 */
public class CodeNarc extends SourceTask implements VerificationTask {
    private AntCodeNarc antCodeNarc = new AntCodeNarc();

    private File reportFile;
    private File configFile;
    private boolean ignoreFailures;

    @TaskAction
    public void check() {
        getLogging().captureStandardOutput(LogLevel.INFO);
        antCodeNarc.execute(getAnt(), getSource(), getConfigFile(), getReportFile(), isIgnoreFailures());
    }

    @InputFile
    public File getConfigFile() {
        return configFile;
    }

    public void setConfigFile(File configFile) {
        this.configFile = configFile;
    }

    @OutputFile
    public File getReportFile() {
        return reportFile;
    }

    public void setReportFile(File reportFile) {
        this.reportFile = reportFile;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * {@inheritDoc}
     */
    public CodeNarc setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
        return this;
    }
}
