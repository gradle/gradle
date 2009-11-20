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
package org.gradle.api.internal.artifacts.publish.maven.deploy;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class ClassifierArtifact {
    private String classifier;
    private String type;
    private File file;

    public ClassifierArtifact(String classifier, String type, File file) {
        this.classifier = classifier;
        this.type = type;
        this.file = file;
    }

    public String getClassifier() {
        return classifier;
    }

    public String getType() {
        return type;
    }

    public File getFile() {
        return file;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClassifierArtifact that = (ClassifierArtifact) o;

        if (classifier != null ? !classifier.equals(that.classifier) : that.classifier != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = classifier != null ? classifier.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (file != null ? file.hashCode() : 0);
        return result;
    }
}
