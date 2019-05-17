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
package org.gradle.api.internal.artifacts.repositories.resolver;

import com.google.common.base.Strings;
import org.apache.ivy.core.IvyPatternHelper;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resource.ExternalResourceName;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

abstract class AbstractResourcePattern implements ResourcePattern {
    public static final String CLASSIFIER_KEY = "classifier";
    private final ExternalResourceName pattern;
    private final boolean revisionIsOptional;
    private final boolean organisationIsOptional;
    private final boolean artifactIsOptional;
    private final boolean classifierIsOptional;
    private final boolean extensionIsOptional;
    private final boolean typeIsOptional;

    public AbstractResourcePattern(String pattern) {
        this(new ExternalResourceName(pattern));

    }

    public AbstractResourcePattern(URI baseUri, String pattern) {
        this(new ExternalResourceName(baseUri, pattern));
    }

    private AbstractResourcePattern(ExternalResourceName pattern) {
        this.pattern = pattern;
        this.revisionIsOptional = isOptionalToken(IvyPatternHelper.REVISION_KEY);
        this.organisationIsOptional = isOptionalToken(IvyPatternHelper.ORGANISATION_KEY, IvyPatternHelper.ORGANISATION_KEY2);
        this.artifactIsOptional = isOptionalToken(IvyPatternHelper.ARTIFACT_KEY);
        this.classifierIsOptional = isOptionalToken(CLASSIFIER_KEY);
        this.extensionIsOptional = isOptionalToken(IvyPatternHelper.EXT_KEY);
        this.typeIsOptional = isOptionalToken(IvyPatternHelper.TYPE_KEY);
    }

    @Override
    public String getPattern() {
        return pattern.getDecoded();
    }

    protected ExternalResourceName getBase() {
        return pattern;
    }

    protected String substituteTokens(String pattern, Map<String, String> attributes) {
        return IvyPatternHelper.substituteTokens(pattern, attributes);
    }

    protected Map<String, String> toAttributes(ModuleComponentArtifactMetadata artifact) {
        Map<String, String> attributes = toAttributes(artifact.getId().getComponentIdentifier());
        attributes.putAll(toAttributes(artifact.getName()));
        return attributes;
    }

    protected Map<String, String> toAttributes(ModuleIdentifier module, IvyArtifactName ivyArtifactName) {
        Map<String, String> attributes = toAttributes(module);
        attributes.putAll(toAttributes(ivyArtifactName));
        return attributes;
    }

    protected Map<String, String> toAttributes(IvyArtifactName ivyArtifact) {
        HashMap<String, String> attributes = new HashMap<String, String>();
        attributes.put(IvyPatternHelper.ARTIFACT_KEY, ivyArtifact.getName());
        attributes.put(IvyPatternHelper.TYPE_KEY, ivyArtifact.getType());
        attributes.put(IvyPatternHelper.EXT_KEY, ivyArtifact.getExtension());
        attributes.put(CLASSIFIER_KEY, ivyArtifact.getClassifier());
        return attributes;
    }

    protected Map<String, String> toAttributes(ModuleIdentifier module) {
        HashMap<String, String> attributes = new HashMap<String, String>();
        attributes.put(IvyPatternHelper.ORGANISATION_KEY, module.getGroup());
        attributes.put(IvyPatternHelper.MODULE_KEY, module.getName());
        return attributes;
    }

    protected Map<String, String> toAttributes(ModuleComponentIdentifier componentIdentifier) {
        HashMap<String, String> attributes = new HashMap<String, String>();
        attributes.put(IvyPatternHelper.ORGANISATION_KEY, componentIdentifier.getGroup());
        attributes.put(IvyPatternHelper.MODULE_KEY, componentIdentifier.getModule());
        attributes.put(IvyPatternHelper.REVISION_KEY, componentIdentifier.getVersion());
        return attributes;
    }

    @Override
    public boolean isComplete(ModuleIdentifier moduleIdentifier) {
        return isValidSubstitute(moduleIdentifier.getName(), false)
            && isValidSubstitute(moduleIdentifier.getGroup(), organisationIsOptional);
    }

    @Override
    public boolean isComplete(ModuleComponentIdentifier componentIdentifier) {
        return isValidSubstitute(componentIdentifier.getModule(), false)
            && isValidSubstitute(componentIdentifier.getGroup(), organisationIsOptional)
            && isValidSubstitute(componentIdentifier.getVersion(), revisionIsOptional);
    }

    @Override
    public boolean isComplete(ModuleComponentArtifactMetadata artifactIdentifier) {
        IvyArtifactName artifactName = artifactIdentifier.getName();
        ModuleComponentIdentifier componentIdentifier = artifactIdentifier.getId().getComponentIdentifier();
        return isValidSubstitute(componentIdentifier.getModule(), false)
            && isValidSubstitute(componentIdentifier.getGroup(), organisationIsOptional)
            && isValidSubstitute(componentIdentifier.getVersion(), revisionIsOptional)
            && isValidSubstitute(artifactName.getName(), artifactIsOptional)
            && isValidSubstitute(artifactName.getClassifier(), classifierIsOptional)
            && isValidSubstitute(artifactName.getExtension(), extensionIsOptional)
            && isValidSubstitute(artifactName.getType(), typeIsOptional);
    }

    private boolean isValidSubstitute(@Nullable String candidate, boolean optional) {
        if (Strings.isNullOrEmpty(candidate)) {
            return optional;
        }
        return !candidate.startsWith("${");
    }

    private boolean isOptionalToken(String... tokenVariants) {
        String patternString = pattern.getPath();
        int tokenIndex = -1;
        for (String token : tokenVariants) {
            tokenIndex = patternString.indexOf("[" + token + "]");
            if (tokenIndex != -1) {
                break;
            }
        }
        if (tokenIndex == -1) {
            return true;
        }

        int optionalOpen = 0;
        for (int i = 0; i < tokenIndex; i++) {
            char nextChar = patternString.charAt(i);
            if (nextChar == '(') {
                optionalOpen++;
            } else if (nextChar == ')') {
                optionalOpen = Math.max(0, optionalOpen - 1);
            }
        }
        return optionalOpen > 0;
    }
}
