/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.publish.internal.metadata;

import com.google.common.base.Objects;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newLinkedHashSet;

@NotThreadSafe
class InvalidPublicationChecker {

    private static final DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry();

    private final String publicationName;
    private final String taskPath;
    private final BiMap<String, VariantIdentity> variants = HashBiMap.create();
    private final List<String> errors = newArrayList();
    private final Set<String> explanations = newLinkedHashSet();
    private boolean publicationHasVersion = false;
    private boolean publicationHasDependencyOrConstraint = false;

    public InvalidPublicationChecker(String publicationName, String taskPath) {
        this.publicationName = publicationName;
        this.taskPath = taskPath;
    }

    public void registerVariant(String name, AttributeContainer attributes, Set<? extends Capability> capabilities) {
        if (attributes.isEmpty()) {
            failWith("Variant '" + name + "' must declare at least one attribute.");
        }
        if (variants.containsKey(name)) {
            failWith("It is invalid to have multiple variants with the same name ('" + name + "')");
        } else {
            VariantIdentity identity = new VariantIdentity(attributes, capabilities);
            if (variants.containsValue(identity)) {
                String found = variants.inverse().get(identity);
                failWith("Variants '" + found + "' and '" + name + "' have the same attributes and capabilities. Please make sure either attributes or capabilities are different.");
            } else {
                variants.put(name, identity);
            }
        }
    }

    private void checkVariantDependencyVersions() {
        if (publicationHasDependencyOrConstraint && !publicationHasVersion) {
            // Previous variant did not declare any version
            failWith("Publication only contains dependencies and/or constraints without a version. " +
                "You need to add minimal version information, publish resolved versions (" + DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("on this", "publishing_maven", "publishing_maven:resolved_dependencies") + ") or " +
                "reference a platform (" + DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("platforms", "platforms") + ")");
        }
    }

    public void validate() {
        if (variants.isEmpty()) {
            failWith("This publication must publish at least one variant");
        }
        checkVariantDependencyVersions();
        if (!errors.isEmpty()) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Invalid publication '" + publicationName + "'");
            formatter.startChildren();
            for (String error : errors) {
                formatter.node(error);
            }
            formatter.endChildren();
            for (String explanation : explanations) {
                formatter.node(explanation);
            }
            throw new InvalidUserCodeException(formatter.toString());
        }
    }

    private void failWith(String message) {
        failWith(message, null);
    }

    private void failWith(String message, @Nullable String explanation) {
        errors.add(message);
        if (explanation != null) {
            explanations.add(explanation);
        }
    }

    public void sawVersion() {
        publicationHasVersion = true;
    }

    public void sawDependencyOrConstraint() {
        publicationHasDependencyOrConstraint = true;
    }

    public void addDependencyValidationError(String variant, String errorMessage, String genericExplanation, String suppressor) {
        failWith("Variant '" + variant + "' " + errorMessage,
            genericExplanation + explainHowToSuppress(suppressor)
        );
    }

    private String explainHowToSuppress(String suppressor) {
        return " If you did this intentionally you can disable this check by adding '" + suppressor + "' to the suppressed validations of the " + taskPath + " task. " +
            DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("on suppressing validations", "publishing_setup", "sec:suppressing_validation_errors");
    }

    private static final class VariantIdentity {
        private final AttributeContainer attributes;
        private final Set<? extends Capability> capabilities;

        private VariantIdentity(AttributeContainer attributes, Set<? extends Capability> capabilities) {
            this.attributes = attributes;
            this.capabilities = capabilities;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            VariantIdentity that = (VariantIdentity) o;
            return Objects.equal(attributes, that.attributes) &&
                Objects.equal(capabilities, that.capabilities);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(attributes, capabilities);
        }
    }
}
