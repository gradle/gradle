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

package org.gradle.api.plugins.quality;

import org.gradle.api.reporting.SingleFileReport;

/**
 * The single file HTML report for FindBugs.
 */
public interface FindBugsHtmlReport extends SingleFileReport {

    /**
     * The stylesheet to use to generate the HTML report.
     * <p>
     * If {@code null} or empty, FindBugs will use its default stylesheet.
     *
     * @return the stylesheet to use to generate the HTML report
     */
    String getStylesheet();

    /**
     * The stylesheet to use to generate the report.
     *
     * @see <a href="http://findbugs.sourceforge.net/manual/running.html#commandLineOptions">the FindBugs documentation</a>
     * for a list of the stylesheets bundled with FindBugs
     * @param stylesheet the stylesheet to use to generate the HTML report
     */
    void setStylesheet(String stylesheet);

}
