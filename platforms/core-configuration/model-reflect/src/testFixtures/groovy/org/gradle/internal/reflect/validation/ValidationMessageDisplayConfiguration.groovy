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


import static org.gradle.util.internal.TextUtil.endLineWithDot

class ValidationMessageDisplayConfiguration<T extends ValidationMessageDisplayConfiguration<T>> {
    private final ValidationMessageChecker checker
    String pluginId
    boolean hasIntro = true
    private String description
    private String reason
    private List<String> solutions = []

    ValidationMessageDisplayConfiguration(ValidationMessageChecker checker) {
        this.checker = checker
    }

    String typeName
    String property
    String function
    String method
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

    T property(String name) {
        this.property = name
        this
    }

    T function(String name) {
        this.function = name
        this
    }

    T method(String name) {
        this.method = name
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

    String getFunctionIntro() {
        "function"
    }

    String getMethodIntro() {
        "method"
    }

    private String getIntro() {
        if (!hasIntro) {
            return ''
        }
        String intro = typeName ? getTypeIntro() : getPropertyOrFunctionDescription().capitalize()
        if (pluginId) {
            return "In plugin '${pluginId}' ${intro.uncapitalize()}"
        }
        return intro
    }

    private String getMethodDescription() {
        method ? "${methodIntro} '${method}' " : ""
    }

    private String getFunctionDescription() {
        function ? "${functionIntro} '${function}' " : methodDescription
    }

    private String getPropertyOrFunctionDescription() {
        property ? "${propertyIntro} '${property}' " : functionDescription
    }

    private String getTypeIntro() {
        "Type '$typeName' ${propertyOrFunctionDescription}"
    }

    private String getOutro() {
        checker.learnAt(documentationId, section)
    }

    static String formatEntry(String entry) {
        endLineWithDot(entry.capitalize())
    }

    String render(boolean renderSolutions = true) {
        def newLine = "\n${checker.messageIndent}"
        def sb = label(newLine)
        if (reason) {
            sb.append("Reason: ")
                .append(formatEntry(reason))
                .append(newLine)
                .append(newLine)
        }
        if (renderSolutions && !solutions.empty) {
            if (solutions.size() > 1) {
                sb.append("Possible solutions:$newLine")
                solutions.eachWithIndex { String solution, int i ->
                    sb.append("  ")
                        .append(i + 1)
                        .append(". ")
                        .append(formatEntry(solution))
                        .append(newLine)
                }
            } else {
                sb.append("Possible solution: ")
                    .append(formatEntry(solutions[0]))
                    .append(newLine)
            }
            sb.append(newLine)
        }
        if (includeLink) {
            sb.append(outro)
        }
        sb.toString().trim()
    }

    StringBuilder label(String newLine = "") {
        new StringBuilder(intro)
            .append(endLineWithDot(description))
            .append(newLine)
            .append(newLine)
    }
}
