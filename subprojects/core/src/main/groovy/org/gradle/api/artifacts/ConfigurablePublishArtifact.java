/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.artifacts;

/**
 * A {@link PublishArtifact} whose properties can be modified.
 */
public interface ConfigurablePublishArtifact extends PublishArtifact {
    /**
     * Sets the name of this artifact.
     *
     * @param name The name. Should not be null.
     */
    void setName(String name);

    /**
     * Sets the extension of this artifact.
     *
     * @param extension The extension. Should not be null.
     */
    void setExtension(String extension);

    /**
     * Sets the type of this artifact.
     *
     * @param type The type. Should not be null.
     */
    void setType(String type);

    /**
     * Sets the classifier of this artifact.
     *
     * @param classifier The classifier. May be null.
     */
    void setClassifier(String classifier);

    /**
     * Registers some tasks which build this artifact.
     *
     * @param tasks The tasks. These are evaluated as per {@link org.gradle.api.Task#dependsOn(Object...)}.
     * @return this
     */
    ConfigurablePublishArtifact builtBy(Object... tasks);
}
