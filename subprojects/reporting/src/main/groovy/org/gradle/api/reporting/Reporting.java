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

package org.gradle.api.reporting;

import groovy.lang.Closure;

/**
 * An object that provides reporting options
 *
 * @param <T> The base type of the report container
 */
public interface Reporting<T extends ReportContainer> {

    /**
     * Returns the report container.
     *
     * @return The report container
     */
    T getReports();

    /**
     * Allow configuration of the report container by closure.
     *
     * For example…
     *
     * <pre>
     * reports {
     *   html {
     *     enabled false
     *   }
     *   xml.destination "build/reports/myReport.xml"
     * }
     * </pre>
     * @param closure The configuration
     * @return The report container
     */
    T reports(Closure closure);
}
