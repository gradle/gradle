/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.IllegalDependencyNotation;

public class ParsedModuleStringNotation {
    private String group;
    private String name;
    private String version;
    private String classifier;
    private String artifactType;

    public ParsedModuleStringNotation(String moduleNotation, String artifactType) {
        assignValuesFromModuleNotation(moduleNotation);
        this.artifactType = artifactType;
    }

    private void assignValuesFromModuleNotation(String moduleNotation) {
        int count = 0;
        int idx = 0;
        int cur = -1;
        while (++cur < moduleNotation.length()) {
            if (':' == moduleNotation.charAt(cur)) {
                String fragment = moduleNotation.substring(idx, cur);
                assignValue(count, fragment);
                idx = cur + 1;
                count++;
            }
        }
        assignValue(count, moduleNotation.substring(idx, cur));
        count++;
        if (count < 2 || count > 4) {
            throw new IllegalDependencyNotation("Supplied String module notation '" + moduleNotation + "' is invalid. Example notations: 'org.gradle:gradle-core:2.2', 'org.mockito:mockito-core:1.9.5:javadoc'.");
        }
    }

    private void assignValue(int count, String fragment) {
        switch (count) {
            case 0:
                group = "".equals(fragment) ? null : fragment;
                break;
            case 1:
                name = fragment;
                break;
            case 2:
                version = "".equals(fragment) ? null : fragment;
                break;
            case 3:
                classifier = fragment;
        }
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getClassifier() {
        return classifier;
    }

    public String getArtifactType() {
        return artifactType;
    }
}
