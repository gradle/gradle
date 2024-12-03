/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publisher;

import org.gradle.api.NonNullApi;
import org.gradle.api.publish.ivy.IvyArtifact;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;

@NonNullApi
public class NormalizedIvyArtifact implements Serializable {
    private final File file;
    private final String extension;
    @Nullable
    private final String classifier;
    private final String name;
    private final String type;
    @Nullable
    private final String conf;

    public NormalizedIvyArtifact(IvyArtifact artifact) {
        this.name = artifact.getName().get();
        this.type = artifact.getType().get();
        this.conf = artifact.getConf().getOrNull();
        this.file = artifact.getFile().get().getAsFile();
        this.extension = artifact.getExtension().get();
        this.classifier = artifact.getClassifier().getOrNull();
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    @Nullable
    public String getConf() {
        return conf;
    }

    public String getExtension() {
        return extension;
    }

    @Nullable
    public String getClassifier() {
        return classifier;
    }

    public File getFile() {
        return file;
    }
}
