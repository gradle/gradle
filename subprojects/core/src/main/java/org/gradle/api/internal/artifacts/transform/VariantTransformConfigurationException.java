/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.GradleException;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.exceptions.Contextual;

import java.util.Arrays;
import java.util.List;

/**
 * An exception to report a problem registering or configuring an Artifact Transform that also provides helpful resolutions.
 */
@Contextual
public final class VariantTransformConfigurationException extends GradleException {
    private static final String RUN_REPORT_SUGGESTION = "Run the 'artifactTransforms' report task to view details about registered transforms.";

    @SuppressWarnings("this-escape")
    public VariantTransformConfigurationException(String message, Throwable cause, DocumentationRegistry documentationRegistry) {
        super(message, cause, buildStandardResolutions(documentationRegistry));
    }

    @SuppressWarnings("this-escape")
    public VariantTransformConfigurationException(String message, DocumentationRegistry documentationRegistry) {
        super(message, buildStandardResolutions(documentationRegistry));
    }

    private static List<String> buildStandardResolutions(DocumentationRegistry documentationRegistry) {
        return Arrays.asList(RUN_REPORT_SUGGESTION, "Review the documentation on Artifact Transforms at " + documentationRegistry.getDocumentationFor("artifact_transforms") + ".");
    }
}
