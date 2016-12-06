/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.attributes.AttributeContainer;

import java.util.Map;

/**
 * Represents some variant of an outgoing configuration.
 *
 * @since 3.3
 */
@Incubating
public interface ConfigurationVariant extends Named {
    /**
     * Returns the attributes that define this variant.
     */
    AttributeContainer getAttributes();

    /**
     * Defines some attributes for this variant.
     */
    ConfigurationVariant attributes(Map<String, String> attributes);

    /**
     * Defines an attribute for this variant.
     */
    ConfigurationVariant attribute(String attributeName, String value);

    /**
     * Returns the artifacts associated with this variant.
     */
    PublishArtifactSet getArtifacts();

    /**
     * Adds an artifact to this variant.
     */
    void artifact(Object notation);

    /**
     * Adds an artifact to this variant, configuring it using the given action.
     */
    void artifact(Object notation, Action<? super ConfigurablePublishArtifact> configureAction);
}
