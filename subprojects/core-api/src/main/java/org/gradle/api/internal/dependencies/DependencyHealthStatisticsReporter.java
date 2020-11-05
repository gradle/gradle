/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.dependencies;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

public class DependencyHealthStatisticsReporter {
    private final StyledTextOutputFactory textOutputFactory;

    public DependencyHealthStatisticsReporter(final StyledTextOutputFactory textOutputFactory) {
        this.textOutputFactory = textOutputFactory;
    }

    public void buildFinished(DependencyHealthStatistics statistics) {
        long total = statistics.getTotal();
        long totalVulnerable =
            statistics.getCritical() +
                statistics.getHigh() +
                statistics.getMedium() +
                statistics.getLow();
        if (total > 0 && totalVulnerable > 0) {
            String pluralizedDependencies = total > 1 ? "dependencies" : "dependency";
            StyledTextOutput textOutput = textOutputFactory.create(DependencyHealthStatisticsReporter.class, LogLevel.LIFECYCLE);
            textOutput.format("%d %s analyzed for vulnerabilities:", total, pluralizedDependencies);
            boolean printDetail = formatDetail(textOutput, statistics.getSuppressed(), "suppressed", false);
            printDetail = formatDetail(textOutput, statistics.getLow(), "low", false);
            printDetail = formatDetail(textOutput, statistics.getMedium(), "medium", printDetail);
            printDetail = formatDetail(textOutput, statistics.getHigh(), "high", printDetail);
            formatDetail(textOutput, statistics.getCritical(), "critical", printDetail);
            textOutput.append(" -- ");
            textOutput.format("For more information run the `dependencyAudit` tasks");
            textOutput.println();
        }
    }

    /**
     * Copied from {@link org.gradle.internal.buildevents.TaskExecutionStatisticsReporter#formatDetail}
     */
    private static boolean formatDetail(StyledTextOutput textOutput, Number count, String title, boolean alreadyPrintedDetail) {
        if (count.equals(0)) {
            return alreadyPrintedDetail;
        }
        if (alreadyPrintedDetail) {
            textOutput.format(",");
        }
        textOutput.format(" %d %s", count, title);
        return true;
    }
}
