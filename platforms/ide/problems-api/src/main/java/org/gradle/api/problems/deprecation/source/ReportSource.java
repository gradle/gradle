/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.problems.deprecation.source;

import org.gradle.api.Incubating;

/**
 * Structural information about the source of a deprecation.
 *
 * @since 8.14
 */
@Incubating
public abstract class ReportSource {

    /**
     * Protected constructor to prevent direct instantiation.
     *
     * @since 8.14
     */
    protected ReportSource() {}

    /**
     * An identifier unique to the report source type.
     *
     * @since 8.14
     */
    public abstract String getId();

    /**
     * Returns a report source marking a plugin as the source of the deprecation.
     *
     * @since 8.14
     */
    public static ReportSource plugin(String id) {
        return new PluginReportSource(id);
    }

}
