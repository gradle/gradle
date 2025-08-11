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

import com.google.common.base.Splitter;
import org.gradle.api.IllegalDependencyNotation;
import org.jspecify.annotations.Nullable;

public class ParsedModuleStringNotation {
    // Limit to 5 parts, to allow the error condition below.
    private static final Splitter COLON_SPLITTER = Splitter.on(':').limit(5);

    @Nullable
    private final String group;
    @Nullable
    private final String name;
    @Nullable
    private final String version;
    @Nullable
    private final String classifier;
    @Nullable
    private final String artifactType;

    public ParsedModuleStringNotation(String moduleNotation, @Nullable String artifactType) {
        String group = null;
        String name = null;
        String version = null;
        String classifier = null;

        int index = 0;
        for (String part : COLON_SPLITTER.split(moduleNotation)) {
            switch (index) {
                case 0:
                    group = part.isEmpty() ? null : part;
                    break;
                case 1:
                    name = part;
                    break;
                case 2:
                    version = part.isEmpty() ? null : part;
                    break;
                case 3:
                    classifier = part;
                    break;
            }
            index++;
        }

        if (index < 2 || index > 4) {
            throw new IllegalDependencyNotation("Supplied String module notation '" + moduleNotation + "' is invalid. Example notations: 'org.gradle:gradle-core:2.2', 'org.mockito:mockito-core:1.9.5:javadoc'.");
        }

        this.group = group;
        this.name = name;
        this.version = version;
        this.classifier = classifier;
        this.artifactType = artifactType;
    }

    @Nullable
    public String getGroup() {
        return group;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getVersion() {
        return version;
    }

    @Nullable
    public String getClassifier() {
        return classifier;
    }

    @Nullable
    public String getArtifactType() {
        return artifactType;
    }
}
