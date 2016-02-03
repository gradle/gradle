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

package org.gradle.integtests.tooling.fixture

import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.util.GradleVersion

@CompileStatic
trait ToolingModelTestTrait {
    // Only check serialization with 2.12+ since Tooling Model proxy serialization is broken in previous TAPI clients
    private boolean shouldCheckSerialization = GradleVersion.current().getBaseVersion() >= GradleVersion.version("2.12")

    abstract <T> T withConnection(@DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl);

    EclipseProject loadEclipseProjectModel() {
        loadToolingModel(EclipseProject)
    }

    public <T> T loadToolingModel(Class<T> modelClass) {
        def model = withConnection { connection -> connection.getModel(modelClass) }
        assertToolingModelIsSerializable(model)
        model
    }

    void assertToolingModelIsSerializable(object) {
        if (shouldCheckSerialization) {
            // only check that no exceptions are thrown during serialization
            // cross-version deserialization would require using PayloadSerializer
            ObjectOutputStream oos = new ObjectOutputStream(new NullOutputStream());
            try {
                oos.writeObject(object);
            } finally {
                oos.flush();
            }
        }
    }

    private static class NullOutputStream extends OutputStream {
        @Override
        void write(int b) throws IOException {
            // ignore
        }
    }
}


