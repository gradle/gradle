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

package org.gradle.api.java.archives.internal;

import org.gradle.api.java.archives.Manifest;

import java.io.OutputStream;

/**
 * Internal protocol for Manifest.
 *
 * @since 2.14
 */
public interface ManifestInternal extends Manifest {

    /**
     * See {@link org.gradle.jvm.tasks.Jar#getManifestContentCharset()}
     */
    String getContentCharset();

    /**
     * See {@link org.gradle.jvm.tasks.Jar#getManifestContentCharset()}
     */
    void setContentCharset(String contentCharset);

    /**
     * Writes the manifest into a stream.
     *
     * The manifest will be encoded using the character set defined by the {@link #getContentCharset()} property.
     *
     * @param outputStream The stream to write the manifest to
     * @return this
     */
    Manifest writeTo(OutputStream outputStream);

}
