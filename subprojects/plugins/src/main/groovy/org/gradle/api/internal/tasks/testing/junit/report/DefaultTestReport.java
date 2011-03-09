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

import groovy.util.IndentPrinter;
import groovy.xml.MarkupBuilder;
import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;

public class DefaultTestReport implements TestReporter {
    private File resultDir;
    private File reportDir;

    public void setTestResultsDir(File resultDir) {
        this.resultDir = resultDir;
    }

    public void setTestReportDir(File reportDir) {
        this.reportDir = reportDir;
    }

    public void generateReport() {
        AllTestResults model = loadModel();
        generateFiles(model);
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
                DecimalFormat format = new DecimalFormat("#.#");
                format.setParseBigDecimal(true);
                BigDecimal duration = (BigDecimal) format.parse(testCase.getAttribute("time"));
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

            copyResources();

        } catch (Exception e) {
            throw new GradleException(String.format("Could not generate test report to '%s'.", reportDir), e);
        }
    }

    private <T extends CompositeTestResults> void generatePage(T model, PageRenderer<T> renderer, File outputFile) throws IOException {
        outputFile.getParentFile().mkdirs();
        OutputStream outputStream = new FileOutputStream(outputFile);
        try {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "utf-8"));
            writer.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">");
            MarkupBuilder markupBuilder = new MarkupBuilder(new IndentPrinter(writer, ""));
            renderer.render(markupBuilder, model);
            writer.flush();
        } finally {
            outputStream.close();
        }
    }

    private void copyResources() throws IOException {
        copyResource("style.css");
        copyResource("report.js");
        copyResource("css3-pie-1.0beta3.htc");
    }

    private void copyResource(String resourceName) throws IOException {
        File cssFile = new File(reportDir, resourceName);
        OutputStream outputStream = new FileOutputStream(cssFile);
        try {
            InputStream cssResource = getClass().getResourceAsStream(resourceName);
            try {
                IOUtils.copy(cssResource, outputStream);
            } finally {
                cssResource.close();
            }
        } finally {
            outputStream.close();
        }
    }
}
