/*
 * Copyright 2010 the original author or authors.
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
 * <p>An {@code Artifact} represents an artifact included in a {@link org.gradle.api.artifacts.Dependency}.</p>
 * An artifact is an (immutable) value object.
 */
public interface DependencyArtifact {
    String DEFAULT_TYPE = "jar";

    /**
     * Returns the name of this artifact.
     */
    String getName();

    /**
     * Sets the name of this artifact.
     */
    void setName(String name);

    /**
     * Returns the type of this artifact. Often the type is the same as the extension,
     * but sometimes this is not the case. For example for an ivy XML module descriptor, the type is
     * <em>ivy</em> and the extension is <em>xml</em>.
     *
     * @see #getExtension() 
     */
    String getType();

    /**
     * Sets the type of this artifact.
     */
    void setType(String type);

    /**
     * Returns the extension of this artifact. Often the extension is the same as the type,
     * but sometimes this is not the case. For example for an ivy XML module descriptor, the type is
     * <em>ivy</em> and the extension is <em>xml</em>.
     *
     * @see #getType() 
     */
    String getExtension();

    /**
     * Sets the extension of this artifact.
     */
    void setExtension(String extension);

    /**
     * Returns the classifier of this artifact.
     */
    String getClassifier();

    /**
     * Sets the classifier of this artifact.
     */
    void setClassifier(String classifier);

    /**
     * Returns an URL under which this artifact can be retrieved. If not
     * specified the user repositories are used for retrieving. 
     */
    String getUrl();

    /**
     * Sets the URL for this artifact.
     */
    void setUrl(String url);
}
