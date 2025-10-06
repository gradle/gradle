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
package org.gradle.tooling.model.build;

import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.Model;

/**
 * Plaintext content printed by {@code gradle --version} (the banner including build time, JVM, OS, etc.).
 * The content is produced by the target Gradle distribution without starting the daemon.
 *
 * @since 9.3.0
 */
public interface VersionBanner extends Model, BuildModel {
    /**
     * Returns the exact banner text as printed by {@code gradle --version} for the target distribution.
     *
     * @since 9.3.0
     */
    String getVersionOutput();
}

