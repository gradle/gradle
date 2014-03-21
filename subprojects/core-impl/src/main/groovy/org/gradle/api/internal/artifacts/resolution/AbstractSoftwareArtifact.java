/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.resolution;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.resolution.SoftwareArtifact;

import java.io.File;

public abstract class AbstractSoftwareArtifact implements SoftwareArtifact {
    private final File file;
    private final GradleException failure;

    protected AbstractSoftwareArtifact(File file) {
        this.file = file;
        failure = null;
    }

    protected AbstractSoftwareArtifact(GradleException failure) {
        file = null;
        this.failure = failure;
    }

    public File getFile() throws GradleException {
        assertNoFailure();
        return file;
    }

    public GradleException getFailure() {
        return failure;
    }

    private void assertNoFailure() {
        if (failure != null) {
            throw failure;
        }
    }
}
