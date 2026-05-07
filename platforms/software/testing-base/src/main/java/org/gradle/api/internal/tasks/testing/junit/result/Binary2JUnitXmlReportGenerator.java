/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.SafeFileLocationUtils;
import org.gradle.internal.nativeintegration.network.HostnameLookup;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.util.internal.GFileUtils;
import org.jspecify.annotations.NullMarked;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NullMarked
public class Binary2JUnitXmlReportGenerator {
    private static final String REPORT_FILE_PREFIX = "TEST-";
    private static final String REPORT_FILE_EXTENSION = ".xml";

    private final File testResultsDir;
    private final TestResultsProvider testResultsProvider;

    @VisibleForTesting
    JUnitXmlResultWriter xmlWriter;

    private final BuildOperationRunner buildOperationRunner;
    private final BuildOperationExecutor buildOperationExecutor;
    private final static Logger LOG = Logging.getLogger(Binary2JUnitXmlReportGenerator.class);

    @Inject
    public Binary2JUnitXmlReportGenerator(
        BuildOperationRunner buildOperationRunner, BuildOperationExecutor buildOperationExecutor, HostnameLookup hostnameLookup,
        File testResultsDir, TestResultsProvider testResultsProvider, JUnitXmlResultOptions options
    ) {
        this.testResultsDir = testResultsDir;
        this.testResultsProvider = testResultsProvider;
        this.xmlWriter = new JUnitXmlResultWriter(testResultsDir.toPath(), hostnameLookup.getHostname(), testResultsProvider, options);
        this.buildOperationRunner = buildOperationRunner;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public void generate() {
        Timer clock = Time.startTimer();

        buildOperationRunner.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                File[] oldXmlFiles = testResultsDir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.startsWith("TEST") && name.endsWith(".xml");
                    }
                });

                for (File oldXmlFile : oldXmlFiles) {
                    GFileUtils.deleteQuietly(oldXmlFile);
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Delete old JUnit XML results");
            }
        });

        // Collect all results first to detect duplicate class names
        List<TestClassResult> allResults = new ArrayList<>();
        testResultsProvider.visitClasses(allResults::add);

        // Count occurrences of each class name
        Map<String, Integer> classNameCounts = new HashMap<>();
        for (TestClassResult result : allResults) {
            classNameCounts.merge(result.getClassName(), 1, Integer::sum);
        }

        // Track current index for each duplicate class name
        Map<String, Integer> classNameCurrentIndex = new HashMap<>();

        buildOperationExecutor.runAll((BuildOperationQueue<JUnitXmlReportFileGenerator> queue) -> {
            for (TestClassResult result : allResults) {
                String className = result.getClassName();
                String fileName;
                if (classNameCounts.get(className) > 1) {
                    // Duplicate class name - add numeric suffix
                    int index = classNameCurrentIndex.merge(className, 1, Integer::sum);
                    fileName = getReportFileName(className, index);
                } else {
                    // Unique class name - no suffix needed
                    fileName = getReportFileName(className, 0);
                }
                File reportFile = new File(testResultsDir, fileName);
                queue.add(new JUnitXmlReportFileGenerator(result, reportFile, xmlWriter));
            }
        });

        LOG.info("Finished generating test XML results ({}) into: {}", clock.getElapsed(), testResultsDir);
    }

    private static String getReportFileName(String className, int index) {
        String suffix = index > 0 ? "-" + index : "";
        return SafeFileLocationUtils.toSafeFileName(REPORT_FILE_PREFIX, className + suffix + REPORT_FILE_EXTENSION, false);
    }

    private static class JUnitXmlReportFileGenerator implements RunnableBuildOperation {
        private final TestClassResult result;
        private final File reportFile;
        private final JUnitXmlResultWriter xmlWriter;

        public JUnitXmlReportFileGenerator(TestClassResult result, File reportFile, JUnitXmlResultWriter xmlWriter) {
            this.result = result;
            this.reportFile = reportFile;
            this.xmlWriter = xmlWriter;
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Generate junit XML test report for ".concat(result.getClassName()));
        }

        @Override
        public void run(BuildOperationContext context) {
            try (FileOutputStream output = new FileOutputStream(reportFile)) {
                xmlWriter.write(result, output);
            } catch (Exception e) {
                throw new GradleException(String.format("Could not write XML test results for %s to file %s.", result.getClassName(), reportFile), e);
            }
        }
    }
}
