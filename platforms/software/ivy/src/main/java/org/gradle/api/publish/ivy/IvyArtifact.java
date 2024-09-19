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

import org.gradle.api.provider.Property;
import org.gradle.api.publish.PublicationArtifact;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

/**
 * An artifact published as part of a {@link IvyPublication}.
 */
public interface IvyArtifact extends PublicationArtifact {
    /**
     * The name used to publish the artifact file.
     * Defaults to the name of the module that this artifact belongs to.
     */
    @ReplacesEagerProperty
    Property<String> getName();

    /**
     * The type used to publish the artifact file.
     */
    @ReplacesEagerProperty
    Property<String> getType();

    /**
     * The extension used to publish the artifact file.
     * For an artifact without an extension, this value will be an empty String.
     */
    @ReplacesEagerProperty
    Property<String> getExtension();

    /**
     * The classifier used to publish the artifact file.
     * An absent value (the default) indicates that this artifact will be published without a classifier.
     */
    @Optional
    @ReplacesEagerProperty
    Property<String> getClassifier();

    /**
     * A comma separated list of public configurations in which this artifact is published.
     * The '*' wildcard is used to designate that the artifact is published in all public configurations.
     * An optional value (the default) indicates that this artifact will be published without a conf attribute.
     */
    @Optional
    @ReplacesEagerProperty
    Property<String> getConf();
}
