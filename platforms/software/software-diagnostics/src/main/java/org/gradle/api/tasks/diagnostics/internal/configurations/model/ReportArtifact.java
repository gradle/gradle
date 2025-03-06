/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.configurations.model;

import javax.annotation.Nullable;

/**
 * Lightweight, immutable model of an artifact for configuration reporting.
 */
public final class ReportArtifact {
    private final String name;
    private final String displayName;
    @Nullable
    private final String classifier;
    private final String type;

    ReportArtifact(String name, String displayName, @Nullable String classifier, String type) {
        this.name = name;
        this.displayName = displayName;
        this.classifier = classifier;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    public String getClassifier() {
        return classifier;
    }

    public String getType() {
        return type;
    }

}
