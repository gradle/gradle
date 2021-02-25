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
    boolean hasIntro = true

    ValidationMessageDisplayConfiguration(ValidationMessageChecker checker) {
        this.checker = checker
    }

    String typeName
    String property
    String section
    boolean includeLink = false

    T property(String name) {
        property = name
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

    private String getIntro() {
        if (hasIntro) {
            typeName ? "Type '$typeName': ${property ? "property '${property}' " : ''}" : (property ? "Property '${property}' " : "")
        } else {
            ''
        }
    }

    private String getOutro() {
        includeLink ? " ${checker.learnAt("validation_problems", section)}." : ""
    }

    String render(String mainBody) {
        if (!(mainBody.endsWith('.'))) {
            mainBody = "${mainBody}."
        }
        String rendered = "${intro}${mainBody}${outro}"
        rendered
    }
}
