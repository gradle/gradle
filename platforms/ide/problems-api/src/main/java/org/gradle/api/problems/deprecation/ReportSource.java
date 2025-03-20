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

package org.gradle.api.problems.deprecation;

import org.gradle.api.Incubating;

/**
 * TODO documentation.
 *
 * @since 8.14
 */
@Incubating
public abstract class ReportSource {

    /**
     * TODO documentation.
     *
     * @since 8.14
     */
    private ReportSource() {
    }

    /**
     * TODO documentation.
     *
     * @since 8.14
     */
    public abstract String name();

    /**
     * TODO documentation.
     *
     * @since 8.14
     */
    @Incubating
    public static class PluginReportSource extends ReportSource {

        private final String id;

        /**
         * TODO documentation.
         *
         * @since 8.14
         */
        public PluginReportSource(String id) {
            this.id = id;
        }

        /**
         * TODO documentation.
         *
         * @since 8.14
         */
        public String getId() {
            return id;
        }

        /**
         * TODO documentation.
         *
         * @since 8.14
         */
        @Override
        public String name() {
            return id;
        }
    }

    /**
     * TODO documentation.
     *
     * @since 8.14
     */
    @Incubating
    public static class GradleReportSource extends ReportSource {
        static final ReportSource GRADLE = new GradleReportSource();

        /**
         * TODO documentation.
         *
         * @since 8.14
         */
        private GradleReportSource() {
        }

        /**
         * TODO documentation.
         *
         * @since 8.14
         */
        public String getId() {
            return "gradle";
        }

        /**
         * TODO documentation.
         *
         * @since 8.14
         */
        @Override
        public String name() {
            return "gradle";
        }
    }

    // TODO (donat) Add more sources (build logic)

    /**
     * TODO documentation.
     *
     * @since 8.14
     */
    public static ReportSource plugin(String id) {
        return new PluginReportSource(id);
    }

    /**
     * TODO documentation.
     *
     * @since 8.14
     */
    public static ReportSource gradle() {
        return GradleReportSource.GRADLE;
    }
 }
