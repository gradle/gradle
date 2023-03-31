/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.reflect.validation

class ValidationMessageDisplayConfiguration<T extends ValidationMessageDisplayConfiguration<T>> {
    private final ValidationMessageChecker checker
    String pluginId
    boolean hasIntro = true
    private String description
    private String reason
    private List<String> solutions = []
    boolean skipSolutions = false;

    ValidationMessageDisplayConfiguration(ValidationMessageChecker checker) {
        this.checker = checker
    }

    String typeName
    String property
    String propertyType
    String section
    String documentationId = "validation_problems"
    boolean includeLink = false

    T inPlugin(String pluginId) {
        this.pluginId = pluginId
        this
    }

    T description(String description) {
        this.description = description
        this
    }

    T reason(String reason) {
        this.reason = reason
        this
    }

    T solution(String solution) {
        this.solutions << solution
        this
    }

    T forceSolutionSkip(boolean skipSolutions = true) {
        this.skipSolutions = skipSolutions
        this
    }

    T property(String name) {
        property = name
        this
    }

    T propertyType(String propertyType) {
        this.propertyType = propertyType
        this
    }

    T type(String name) {
        typeName = name
        this
    }

    T includeLink() {
        includeLink = true
        this
    }

    T noIntro() {
        hasIntro = false
        this
    }

    T documentationSection(String documentationSection) {
        section = documentationSection
        this
    }

    T documentationId(String id) {
        documentationId = id
        this
    }

    String getPropertyIntro() {
        "property"
    }

    private String getIntro() {
        if (hasIntro) {
            String intro = typeName ? "Type '$typeName' ${property ? "${propertyIntro} '${property}' " : ''}" : (property ? "${propertyIntro.capitalize()} '${property}' " : "")
            if (pluginId) {
                return "In plugin '${pluginId}' ${intro.uncapitalize()}"
            } else {
                return intro
            }
        } else {
            ''
        }
    }

    private String getOutro() {
        includeLink ? "${checker.learnAt(documentationId, section)}." : ""
    }

    static String formatEntry(String entry) {
        if (entry.endsWith(".")) {
            entry.capitalize()
        } else {
            "${entry.capitalize()}."
        }
    }

    String render(boolean renderSolutions = true) {
        def newLine = "\n${checker.messageIndent}"
        StringBuilder sb = new StringBuilder(intro)
        sb.append(description)
            .append(description.endsWith(".") ? '' : '.')
            .append(newLine)
            .append(newLine)
        if (reason) {
            sb.append("Reason: ${formatEntry(reason)}${newLine}${newLine}")
        }
        if (!skipSolutions && renderSolutions && !solutions.empty) {
            if (solutions.size() > 1) {
                sb.append("Possible solutions:$newLine")
                solutions.eachWithIndex { String solution, int i ->
                    sb.append("  ").append(i + 1).append(". ${formatEntry(solution)}$newLine")
                }
            } else {
                sb.append("Possible solution: ${formatEntry(solutions[0])}$newLine")
            }
            sb.append(newLine)
        }
        if (outro) {
            sb.append(outro)
        }
        sb.toString().trim()
    }
}
