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

package org.gradle.internal.upgrade.report.problems;

import org.gradle.internal.upgrade.report.ApiUpgradeServiceProvider;
import org.gradle.problems.buildtree.ProblemReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DefaultApiUpgradeProblemReporter implements ProblemReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultApiUpgradeProblemReporter.class);

    private final ApiUpgradeServiceProvider serviceProvider;

    @Inject
    public DefaultApiUpgradeProblemReporter(ApiUpgradeServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @Override
    public String getId() {
        return "api-upgrades";
    }

    @Override
    public void report(File reportDir, Consumer<? super Throwable> validationFailures) {
        ApiUpgradeProblemCollector collector = serviceProvider.getProblemCollector();
        if (collector.getDetectedProblems().isEmpty()) {
            return;
        }
        List<String> problems = collector.getDetectedProblems().stream()
            .sorted()
            .collect(Collectors.toList());
        File outputFile = new File(reportDir, "api-upgrades/output.txt");
        try {
            problems.forEach(System.out::println);
            outputFile.getParentFile().mkdirs();
            Files.write(outputFile.toPath(), problems);
            LOGGER.warn("\nFound {} API upgrade problems. Report is located in {}", problems.size(), outputFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.warn("\nCouldn't write API upgrade problem report to a file {}", outputFile.getAbsolutePath(), e);
        }
    }
}
