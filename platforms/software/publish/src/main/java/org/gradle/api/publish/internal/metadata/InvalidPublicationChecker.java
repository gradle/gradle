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
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.publish.internal.validation.PublicationErrorChecker;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@NotThreadSafe
public class InvalidPublicationChecker {

    private static final String DEPENDENCIES_WITHOUT_VERSION_SUPPRESSION = "dependencies-without-versions";;

    private static final DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry();

    private final String publicationName;
    private final String taskPath;
    private final BiMap<String, VariantIdentity> variants = HashBiMap.create();
    private final List<String> errors = new ArrayList<>();
    private final Set<String> explanations = new LinkedHashSet<>();
    private final Set<String> suppressedValidationErrors;
    private boolean publicationHasVersion = false;
    private boolean publicationHasDependencyOrConstraint = false;

    public InvalidPublicationChecker(String publicationName, String taskPath, Set<String> suppressedValidationErrors) {
        this.publicationName = publicationName;
        this.taskPath = taskPath;
        this.suppressedValidationErrors = suppressedValidationErrors;
    }

    public void checkComponent(SoftwareComponent component) {
        if (component instanceof SoftwareComponentInternal) {
            PublicationErrorChecker.checkForUnpublishableAttributes((SoftwareComponentInternal) component, DOCUMENTATION_REGISTRY);
        }
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
        if (!suppressedValidationErrors.contains(DEPENDENCIES_WITHOUT_VERSION_SUPPRESSION) && publicationHasDependencyOrConstraint && !publicationHasVersion) {
            // Previous variant did not declare any version
            failWith("Publication only contains dependencies and/or constraints without a version. " +
                "You should add minimal version information, publish resolved versions (" + DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("on this", "publishing_maven", "publishing_maven:resolved_dependencies") + ") or " +
                "reference a platform (" + DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("platforms", "platforms") + "). " +
                "Disable this check by adding 'dependencies-without-versions' to the suppressed validations of the " + taskPath + " task.");
        }
    }

    public void validateAttributes(String variant, String group, String name, AttributeContainer attributes) {
        for (DependencyAttributesValidator validator : dependencyAttributeValidators()) {
            Optional<String> error = validator.validationErrorFor(group, name, attributes);
            error.ifPresent(s -> addDependencyValidationError(variant, s, validator.getExplanation(), validator.getSuppressor()));
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

    private List<DependencyAttributesValidator> dependencyAttributeValidators() {
        // Currently limited to a single validator
        EnforcedPlatformPublicationValidator validator = new EnforcedPlatformPublicationValidator();
        if (suppressedValidationErrors.contains(validator.getSuppressor())) {
            return Collections.emptyList();
        }
        return Collections.singletonList(validator);
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
