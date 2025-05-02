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
package org.gradle.api.artifacts.result;

import java.io.File;

/**
 * The result of successfully resolving an artifact.
 *
 * @since 2.0
 */
public interface ResolvedArtifactResult extends ArtifactResult {
    /**
     * The file for the artifact.
     */
    File getFile();

    /**
     * The variant that included this artifact.
     */
    ResolvedVariantResult getVariant();
}
