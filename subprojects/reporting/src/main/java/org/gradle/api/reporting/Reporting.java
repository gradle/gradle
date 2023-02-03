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
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;

/**
 * An object that provides reporting options.
 * <p>
 * Tasks that produce reports as part of their execution expose configuration options of those reports via these methods.
 * The {@code Reporting} interface is parameterized, where the parameter denotes the specific type of reporting container
 * that is exposed. The specific type of the reporting container denotes the different types of reports available.
 * <p>
 * For example, given a task such as:
 * </p>
 * <pre>
 * class MyTask implements Reporting&lt;MyReportContainer&gt; {
 *     // implementation
 * }
 *
 * interface MyReportContainer extends ReportContainer&lt;Report&gt; {
 *     Report getHtml();
 *     Report getCsv();
 * }
 * </pre>
 * <p>
 * The reporting aspects of such a task can be configured as such:
 * </p>
 * <pre>
 * task my(type: MyTask) {
 *     reports {
 *         html.enabled = true
 *         csv.enabled = false
 *     }
 * }
 * </pre>
 * <p>
 * See the documentation for the specific {@code ReportContainer} type for the task for information on report types and options.
 * </p>
 *
 * @param <T> The base type of the report container
 */
public interface Reporting<T extends ReportContainer> {

    /**
     * A {@link ReportContainer} instance.
     * <p>
     * Implementers specify a specific implementation of {@link ReportContainer} that describes the types of reports that
     * are available.
     *
     * @return The report container
     */
    T getReports();

    /**
     * Allow configuration of the report container by closure.
     *
     * <pre>
     * reports {
     *   html {
     *     enabled false
     *   }
     *   xml.destination "build/reports/myReport.xml"
     * }
     * </pre>
     *
     * @param closure The configuration
     * @return The report container
     */
    T reports(@DelegatesTo(type="T", strategy = Closure.DELEGATE_FIRST) Closure closure);

    /**
     * Allow configuration of the report container by closure.
     *
     * <pre>
     * reports {
     *   html {
     *     enabled false
     *   }
     *   xml.destination "build/reports/myReport.xml"
     * }
     * </pre>
     * @param configureAction The configuration
     * @return The report container
     */
    T reports(Action<? super T> configureAction);
}
