/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testing.jacoco.tasks.coverage

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.w3c.dom.Document
import org.w3c.dom.NamedNodeMap
import org.xml.sax.SAXException

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

public class JacocoCheckCoverage extends DefaultTask {

    private static final OrderBy<CoverageViolation> VIOLATION_ORDER =
        new OrderBy([{ violation -> violation.cls }, { violation -> violation.type }])

    /** The Jacoco coverage results for each file and coverage type; populated by {@link JacocoPlugin#apply}. */
    Map<String, Map<CoverageType, CoverageCounter>> coverage

    // Called by Gradle to when running this task.
    @TaskAction
    def verifyCoverage() {
        JacocoPluginExtension extension = getProject().getExtensions().getByType(JacocoPluginExtension.class)
        List<CoverageViolation> violations = applyRules(extension.coverageRules, coverage)
        Collections.sort(violations, VIOLATION_ORDER)
        if (!violations.isEmpty()) {
            getLogger().quiet("Found the following Jacoco coverage violations")
            for (CoverageViolation violation : violations) {
                getLogger().quiet("{}", violation)
            }
            throw new GradleException("Coverage violations found")
        }
    }

    /**
     * Applies the given coverage rules to the observed code coverage observations and returns a list of violating observations.
     */
    static List<CoverageViolation> applyRules(List<Closure<Double>> rules,
                                              Map<String, Map<CoverageType, CoverageCounter>> coverageObservations) {

        List<CoverageViolation> violations = []
        coverageObservations.each { clazz, clazzScores ->
            clazzScores.each { coverageType, coverageCounter ->
                def thresholds = rules
                    .collect({ rule -> rule.call(coverageType, clazz) })
                    .findAll({ threshold -> threshold <= 1.0 }) // Filter out any check requiring more than 100% coverage.

                if (!thresholds.isEmpty()) {
                    def minThreshold = thresholds.min()
                    def total = coverageCounter.missed + coverageCounter.covered
                    if (coverageCounter.covered < minThreshold * total) {
                        violations.add(new CoverageViolation(
                            clazz, minThreshold, coverageType, coverageCounter.covered, total))
                    }
                }
            }
        }

        violations
    }

    /**
     * Returns the Jacoco coverage results as a map <source file> -> (<coverage type> -> (covered cases, missed cases)).
     */
    static def Map<String, Map<CoverageType, CoverageCounter>> extractCoverageFromReport(InputStream jacocoXmlReport) {
        def Map<String, Map<CoverageType, CoverageCounter>> coverage = new HashMap<>()
        Document document = parseJacocoXmlReport(jacocoXmlReport)
        document.getElementsByTagName("sourcefile").each({ sourceFile ->
            String sourceFileName = sourceFile.getAttributes().getNamedItem("name").getNodeValue()
            Map<CoverageType, CoverageCounter> submap =
                coverage.get(sourceFileName, new EnumMap<>(CoverageType.class))
            sourceFile.getChildNodes().each({ counter ->
                if (counter.getNodeName().equals("counter")) {
                    NamedNodeMap map = counter.getAttributes()
                    CoverageType type = CoverageType.valueOf(map.getNamedItem("type").getNodeValue())
                    submap[type] = new CoverageCounter(
                        map.getNamedItem("covered").getNodeValue().toInteger(),
                        map.getNamedItem("missed").getNodeValue().toInteger())
                }
            })
        })

        coverage
    }

    /**
     * Parses the given Jacoco report into a DOM object and returns it.
     */
    static Document parseJacocoXmlReport(InputStream jacocoXmlReport) {
        Document document
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
            factory.setValidating(false)
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            factory.setFeature("http://xml.org/sax/features/namespaces", false)
            DocumentBuilder documentBuilder = factory.newDocumentBuilder()
            document = documentBuilder.parse(jacocoXmlReport)
        } catch (SAXException | IOException e) {
            throw new RuntimeException("Failed to parse Jacoco XML report", e)
        }

        document
    }
}
