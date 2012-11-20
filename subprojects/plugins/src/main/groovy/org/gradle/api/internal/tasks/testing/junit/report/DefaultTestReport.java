/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.junit.report;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.reporting.HtmlReportRenderer;
import org.gradle.util.Clock;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;

public class DefaultTestReport implements TestReporter {
    private final HtmlReportRenderer htmlRenderer = new HtmlReportRenderer();
    private File resultDir;
    private File reportDir;
    private final static Logger LOG = Logging.getLogger(DefaultTestReport.class);

    public DefaultTestReport() {
        htmlRenderer.requireResource(getClass().getResource("/org/gradle/reporting/report.js"));
        htmlRenderer.requireResource(getClass().getResource("/org/gradle/reporting/base-style.css"));
        htmlRenderer.requireResource(getClass().getResource("/org/gradle/reporting/css3-pie-1.0beta3.htc"));
        htmlRenderer.requireResource(getClass().getResource("style.css"));
    }

    public void setTestResultsDir(File resultDir) {
        this.resultDir = resultDir;
    }

    public void setTestReportDir(File reportDir) {
        this.reportDir = reportDir;
    }

    public void generateReport() {
        Clock clock = new Clock();
        AllTestResults model = loadModel();
        generateFiles(model);
        LOG.info("Finished generating test html results (" + clock.getTime() + ")");
    }

    private AllTestResults loadModel() {
        AllTestResults model = new AllTestResults();
        if (resultDir.exists()) {
            for (File file : resultDir.listFiles()) {
                if (file.getName().startsWith("TEST-") && file.getName().endsWith(".xml")) {
                    mergeFromFile(file, model);
                }
            }
        }
        return model;
    }

    private void mergeFromFile(File file, AllTestResults model) {
        try {
            InputStream inputStream = new FileInputStream(file);
            Document document;
            try {
                document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(inputStream));
            } finally {
                inputStream.close();
            }
            NodeList testCases = document.getElementsByTagName("testcase");
            for (int i = 0; i < testCases.getLength(); i++) {
                Element testCase = (Element) testCases.item(i);
                String className = testCase.getAttribute("classname");
                String testName = testCase.getAttribute("name");
                LocaleSafeDecimalFormat format = new LocaleSafeDecimalFormat();
                BigDecimal duration = format.parse(testCase.getAttribute("time"));
                duration = duration.multiply(BigDecimal.valueOf(1000));
                NodeList failures = testCase.getElementsByTagName("failure");
                TestResult testResult = model.addTest(className, testName, duration.longValue());
                for (int j = 0; j < failures.getLength(); j++) {
                    Element failure = (Element) failures.item(j);
                    testResult.addFailure(failure.getAttribute("message"), failure.getTextContent());
                }
            }
            NodeList ignoredTestCases = document.getElementsByTagName("ignored-testcase");
            for (int i = 0; i < ignoredTestCases.getLength(); i++) {
                Element testCase = (Element) ignoredTestCases.item(i);
                String className = testCase.getAttribute("classname");
                String testName = testCase.getAttribute("name");
                model.addTest(className, testName, 0).ignored();
            }
            String suiteClassName = document.getDocumentElement().getAttribute("name");
            ClassTestResults suiteResults = model.addTestClass(suiteClassName);
            NodeList stdOutElements = document.getElementsByTagName("system-out");
            for (int i = 0; i < stdOutElements.getLength(); i++) {
                suiteResults.addStandardOutput(stdOutElements.item(i).getTextContent());
            }
            NodeList stdErrElements = document.getElementsByTagName("system-err");
            for (int i = 0; i < stdErrElements.getLength(); i++) {
                suiteResults.addStandardError(stdErrElements.item(i).getTextContent());
            }
        } catch (Exception e) {
            throw new GradleException(String.format("Could not load test results from '%s'.", file), e);
        }
    }

    private void generateFiles(AllTestResults model) {
        try {
            generatePage(model, new OverviewPageRenderer(), new File(reportDir, "index.html"));
            for (PackageTestResults packageResults : model.getPackages()) {
                generatePage(packageResults, new PackagePageRenderer(), new File(reportDir, packageResults.getName() + ".html"));
                for (ClassTestResults classResults : packageResults.getClasses()) {
                    generatePage(classResults, new ClassPageRenderer(), new File(reportDir, classResults.getName() + ".html"));
                }
            }
        } catch (Exception e) {
            throw new GradleException(String.format("Could not generate test report to '%s'.", reportDir), e);
        }
    }

    private <T extends CompositeTestResults> void generatePage(T model, PageRenderer<T> renderer, File outputFile) throws Exception {
        htmlRenderer.renderer(renderer).writeTo(model, outputFile);
    }
}
