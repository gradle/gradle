/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.tasks.diagnostics.internal;

import org.gradle.api.artifacts.Configuration;

import java.io.IOException;

/**
 * Renders the model of a project dependency report.
 */
public interface DependencyReportRenderer extends ReportRenderer {
    /**
     * Starts rendering the given configuration.
     * @param configuration The configuration.
     */
    void startConfiguration(Configuration configuration);

    /**
     * Writes the given dependency graph for the current configuration.
     *
     * @param configuration The configuration.
     */
    void render(Configuration configuration) throws IOException;

    /**
     * Completes the rendering of the given configuration.
     * @param configuration The configuration
     */
    void completeConfiguration(Configuration configuration);
}
