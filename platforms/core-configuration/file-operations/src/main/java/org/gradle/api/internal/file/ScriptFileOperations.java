/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.file;

import org.jspecify.annotations.NullMarked;

import java.io.File;

/**
 * A {@link FileOperations} that is scoped to a known base directory and can be rebuilt from it.
 *
 * <p>Ordinary {@code FileOperations} instances are recreated by the configuration cache by
 * re-resolving them from the owning build model, which loses the base directory that a script's
 * {@code file(...)} resolves against. Instances of this type instead carry their base directory
 * explicitly, so a type-based codec can serialize just that directory and reconstruct an equivalent
 * instance via {@link DefaultFileOperations#forScript(org.gradle.internal.service.ServiceRegistry, File)}.
 *
 * <p>Only scripts (see {@code DefaultKotlinScript} and {@code DefaultScript}) currently produce such
 * instances, but the codec keys off this type rather than off any script concept.
 *
 * <p>See <a href="https://github.com/gradle/gradle/issues/22879">#22879</a>.
 */
@NullMarked
public interface ScriptFileOperations extends FileOperations {

    /**
     * The base directory this file operations resolves relative paths against.
     */
    File getBaseDir();
}
