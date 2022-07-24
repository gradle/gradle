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

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.logging.text.TreeFormatter;

import java.nio.file.Path;

abstract class AbstractTextDependencyVerificationReportRenderer implements DependencyVerificationReportRenderer {
    protected final Path gradleUserHome;
    protected final DocumentationRegistry documentationRegistry;
    protected TreeFormatter formatter;

    public AbstractTextDependencyVerificationReportRenderer(Path gradleUserHome, DocumentationRegistry documentationRegistry) {
        this.gradleUserHome = gradleUserHome;
        this.documentationRegistry = documentationRegistry;
    }

    protected void legend(String legendItem) {
        formatter.node(legendItem);
    }

    @Override
    public void finish(VerificationHighLevelErrors highLevelErrors) {
        if (highLevelErrors.isMaybeCompromised()) {
            StringBuilder sb = new StringBuilder();
            sb.append("This can indicate that a dependency has been compromised. Please carefully verify the ");
            if (highLevelErrors.hasFailedSignatures()) {
                sb.append("signatures and ");
            }
            sb.append("checksums.");
            if (highLevelErrors.hasFailedSignatures() && highLevelErrors.isKeyServersDisabled()) {
                sb.append(" Key servers are disabled, this can indicate that you need to update the local keyring with the missing keys.");
            }
            legend(sb.toString());
        }
        if (highLevelErrors.canSuggestWriteMetadata()) {
            // the else is just to avoid telling people to use `--write-verification-metadata` if we suspect compromised dependencies
            legend("If the artifacts are trustworthy, you will need to update the gradle/verification-metadata.xml file by following the instructions at " + documentationRegistry.getDocumentationFor("dependency_verification", "sec:troubleshooting-verification"));
        }
    }

    String render() {
        return formatter.toString();
    }
}
