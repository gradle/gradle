/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;

/**
 * This task is <strong>no longer supported</strong> and will <strong>throw an exception</strong> if you try to use it.
 *
 * It is preserved solely for backwards compatibility and may be removed in a future version.
 *
 * @deprecated This class is scheduled for removal in a future version. To upload artifacts, use the maven-publish plugin instead.
 */
@Deprecated // TODO:Finalize Upload Removal - Issue #21439
@DisableCachingByDefault(because = "Produces no cacheable output")
public class Upload extends ConventionTask {
    private final DocumentationRegistry documentationRegistry;

    @Inject
    public Upload(DocumentationRegistry documentationRegistry) {
        this.documentationRegistry = documentationRegistry;
    }

    @TaskAction
    protected void upload() {
        throw new InvalidUserCodeException(
                "The legacy `Upload` task was removed in Gradle 8. Please use the `maven-publish` plugin instead. See " +
                        documentationRegistry.getDocumentationFor("publishing_maven", "publishing_maven") + " for details");
    }
}
