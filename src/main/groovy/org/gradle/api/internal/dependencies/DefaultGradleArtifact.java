/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.dependencies;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.DependencyManager;
import org.gradle.api.dependencies.GradleArtifact;
import org.gradle.api.internal.dependencies.DependenciesUtil;
import org.gradle.util.WrapUtil;
import org.gradle.util.GradleUtil;
import org.gradle.util.GUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultGradleArtifact implements GradleArtifact {
    private String name;
    private String extension;
    private String type;
    private String classifier;

    public DefaultGradleArtifact(String name, String extension, String type, String classifier) {
        this.name = name;
        this.extension = extension;
        this.type = type;
        this.classifier = classifier;
    }

    public Artifact createIvyArtifact(ModuleRevisionId moduleRevisionId) {
        Map extraAttributes = GUtil.isTrue(classifier) ? WrapUtil.toMap(DependencyManager.CLASSIFIER, classifier) : new HashMap();
        return new DefaultArtifact(moduleRevisionId, null, name, type, extension, extraAttributes);
    }

    public String toString() {
        return String.format("Artifact $s:%s:%s:%s", name, type, extension, classifier);
    }

    public String getName() {
        return name;
    }

    public String getExtension() {
        return extension;
    }

    public String getType() {
        return type;
    }

    public String getClassifier() {
        return classifier;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultGradleArtifact that = (DefaultGradleArtifact) o;

        if (classifier != null ? !classifier.equals(that.classifier) : that.classifier != null) return false;
        if (extension != null ? !extension.equals(that.extension) : that.extension != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (type != null ? !type.equals(that.type) : that.type != null) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = (name != null ? name.hashCode() : 0);
        result = 31 * result + (extension != null ? extension.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (classifier != null ? classifier.hashCode() : 0);
        return result;
    }
}
