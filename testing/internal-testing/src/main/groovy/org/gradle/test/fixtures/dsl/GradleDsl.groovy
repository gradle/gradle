/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.test.fixtures.dsl

import groovy.transform.CompileStatic

@CompileStatic
enum GradleDsl {

    GROOVY(".gradle"),
    KOTLIN(".gradle.kts"),
    DECLARATIVE(".gradle.dcl");

    private final String fileExtension;

    GradleDsl(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    String fileNameFor(String fileNameWithoutExtension) {
        return fileNameWithoutExtension + fileExtension;
    }

    String getLanguageCodeName() {
        return name().toLowerCase(Locale.US)
    }

    static List<String> languageCodeNames() {
        return values().collect { GradleDsl val -> val.languageCodeName }
    }
}
