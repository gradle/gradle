/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy;

import org.gradle.api.Buildable;
import org.gradle.api.Incubating;

import javax.annotation.Nullable;
import java.io.File;

/**
 * An artifact published as part of a {@link IvyPublication}.
 */
@Incubating
public interface IvyArtifact extends Buildable {
    /**
     * The name used to publish the artifact file, never <code>null</code>.
     * Defaults to the name of the module that this artifact belongs to.
     */
    String getName();

    /**
     * Sets the name used to publish the artifact file.
     * @param name The name.
     */
    void setName(String name);

    /**
     * The type used to publish the artifact file, never <code>null</code>.
     */
    String getType();

    /**
     * Sets the type used to publish the artifact file.
     * @param type The type.
     */
    void setType(String type);

    /**
     * The extension used to publish the artifact file, never <code>null</code>.
     * For an artifact without an extension, this value will be an empty String.
     */
    String getExtension();

    /**
     * Sets the extension used to publish the artifact file.
     * @param extension The extension.
     */
    void setExtension(String extension);

    /**
     * The classifier used to publish the artifact file.
     * A <code>null</code> value (the default) indicates that this artifact will be published without a classifier.
     */
    @Nullable
    String getClassifier();

    /**
     * Sets the classifier used to publish the artifact file.
     * @param classifier The classifier.
     */
    void setClassifier(@Nullable String classifier);

    /**
     * A comma separated list of public configurations in which this artifact is published.
     * The '*' wildcard is used to designate that the artifact is published in all public configurations.
     * A <code>null</code> value (the default) indicates that this artifact will be published without a conf attribute.
     * @return The value of 'conf' for this artifact.
     */
    @Nullable
    String getConf();

    /**
     * Sets a comma separated list of public configurations in which this artifact is published.
     * The '*' wildcard can be used to designate that the artifact is published in all public configurations.
     * @param conf The value of 'conf' for this artifact.
     */
    void setConf(@Nullable String conf);

    /**
     * The actual file contents to publish.
     */
    File getFile();

    /**
     * Registers some tasks which build this artifact.
     *
     * @param tasks The tasks. These are evaluated as per {@link org.gradle.api.Task#dependsOn(Object...)}.
     */
    void builtBy(Object... tasks);
}
