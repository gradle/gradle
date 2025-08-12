/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.test.fixtures;

import org.gradle.test.fixtures.file.TestFile;

public interface ModuleArtifact {
    /**
     * Returns the path of this artifact relative to the root of the repository.
     */
    String getPath();

    /**
     * Returns the local backing file of this artifact.
     */
    TestFile getFile();

    /**
     * Returns the name of this artifact
     *
     * This will differ from file.name only for Maven unique snapshots
     */
    String getName();
}
