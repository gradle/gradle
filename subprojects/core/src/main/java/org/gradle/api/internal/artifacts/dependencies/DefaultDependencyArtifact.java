/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.DependencyArtifact;
import org.jspecify.annotations.Nullable;

public class DefaultDependencyArtifact implements DependencyArtifact {
    private String name;
    private String type;
    private String extension;
    private String classifier;
    private String url;

    public DefaultDependencyArtifact() {
    }

    public DefaultDependencyArtifact(String name, String type, @Nullable String extension, @Nullable String classifier, @Nullable String url) {
        this.name = name;
        this.type = type;
        this.extension = extension;
        this.classifier = classifier;
        this.url = url;
        validate();
    }

    protected void validate() {
        if (this.name == null) {
            throw new InvalidUserDataException("Artifact name must not be null!");
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public void setType(String type) {
        this.type = type;
    }

    @Nullable
    @Override
    public String getExtension() {
        return extension;
    }

    @Override
    public void setExtension(@Nullable String extension) {
        this.extension = extension;
    }

    @Nullable
    @Override
    public String getClassifier() {
        return classifier;
    }

    @Override
    public void setClassifier(@Nullable String classifier) {
        this.classifier = classifier;
    }

    @Nullable
    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public void setUrl(@Nullable String url) {
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultDependencyArtifact that = (DefaultDependencyArtifact) o;

        if (classifier != null ? !classifier.equals(that.classifier) : that.classifier != null) {
            return false;
        }
        if (extension != null ? !extension.equals(that.extension) : that.extension != null) {
            return false;
        }
        if (!name.equals(that.name)) {
            return false;
        }
        if (type != null ? !type.equals(that.type) : that.type != null) {
            return false;
        }
        if (url != null ? !url.equals(that.url) : that.url != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (extension != null ? extension.hashCode() : 0);
        result = 31 * result + (classifier != null ? classifier.hashCode() : 0);
        result = 31 * result + (url != null ? url.hashCode() : 0);
        return result;
    }
}
