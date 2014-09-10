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
import org.gradle.util.GUtil;

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
        String[] moduleNotationParts = moduleNotation.split(":");
        if (moduleNotationParts.length < 2 || moduleNotationParts.length > 4) {
            throw new IllegalDependencyNotation("The description " + moduleNotation + " is invalid");
        }
        group = GUtil.elvis(moduleNotationParts[0], null);
        name = moduleNotationParts[1];
        version = moduleNotationParts.length == 2 ? null : moduleNotationParts[2];
        if (moduleNotationParts.length == 4) {
            classifier = moduleNotationParts[3];
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
