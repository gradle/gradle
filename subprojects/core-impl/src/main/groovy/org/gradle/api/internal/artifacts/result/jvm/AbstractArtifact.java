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
package org.gradle.api.internal.artifacts.result.jvm;

import org.gradle.api.component.Artifact;
import org.gradle.internal.UncheckedException;

import java.io.File;

public abstract class AbstractArtifact implements Artifact {
    private final File file;
    private final Throwable failure;

    protected AbstractArtifact(File file) {
        this.file = file;
        failure = null;
    }

    protected AbstractArtifact(Throwable failure) {
        file = null;
        this.failure = failure;
    }

    public File getFile() {
        assertNoFailure();
        return file;
    }

    public Throwable getFailure() {
        return failure;
    }

    private void assertNoFailure() {
        if (failure != null) {
            throw UncheckedException.throwAsUncheckedException(failure);
        }
    }
}
