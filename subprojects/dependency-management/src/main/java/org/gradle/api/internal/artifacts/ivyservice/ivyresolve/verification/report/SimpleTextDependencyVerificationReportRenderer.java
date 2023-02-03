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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.report;

import com.google.common.collect.Sets;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.RepositoryAwareVerificationFailure;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.logging.text.TreeFormatter;

import java.nio.file.Path;
import java.util.Set;

/**
 * A text report renderer, which is <i>not</i> cumulative and is used to report failures in
 * a concise way on the console.
 */
class SimpleTextDependencyVerificationReportRenderer extends AbstractTextDependencyVerificationReportRenderer {
    private final Set<String> artifacts = Sets.newLinkedHashSet();

    private ModuleComponentArtifactIdentifier artifact;

    public SimpleTextDependencyVerificationReportRenderer(Path gradleUserHome, DocumentationRegistry documentationRegistry) {
        super(gradleUserHome, documentationRegistry);
    }

    @Override
    public void startNewSection(String title) {
        formatter = new TreeFormatter();
        formatter.node("Dependency verification failed for " + title);
    }

    @Override
    public void startArtifactErrors(Runnable action) {
        action.run();
    }

    @Override
    public void startNewArtifact(ModuleComponentArtifactIdentifier key, Runnable action) {
        artifact = key;
        action.run();
    }

    @Override
    public void reportFailure(RepositoryAwareVerificationFailure failure) {
        artifacts.add(artifact.getDisplayName() + " from repository " + failure.getRepositoryName());
    }

    @Override
    public void finish(VerificationHighLevelErrors highLevelErrors) {
        int size = artifacts.size();
        if (size == 1) {
            formatter.node("One artifact failed verification: " + artifacts.iterator().next());
        } else {
            formatter.node(artifacts.size() + " artifacts failed verification");
            formatter.startChildren();
            for (String artifact : artifacts) {
                formatter.node(artifact);
            }
            formatter.endChildren();
        }
        super.finish(highLevelErrors);
    }

    @Override
    public void reportAsMultipleErrors(Runnable action) {
        action.run();
    }

}
