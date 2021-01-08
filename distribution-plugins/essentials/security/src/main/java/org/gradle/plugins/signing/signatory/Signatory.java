/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.signing.signatory;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * A signatory is an object capable of providing a signature for an arbitrary stream of bytes.
 * @since 4.5
 */
public interface Signatory {

    /**
     * <p>An identifying name for this signatory.</p>
     *
     * <p>The name must be constant for the life of the signatory and should uniquely identify it within a project.</p>
     */
    @Internal
    String getName();

    /**
     * Exhausts {@code toSign}, and writes the signature to {@code signatureDestination}. The caller is responsible for closing the streams, though the output WILL be flushed.
     *
     * @param toSign The source of the data to be signed
     * @param destination Where the signature will be written to
     */
    void sign(InputStream toSign, OutputStream destination);

    /**
     * Exhausts {@code toSign}, and returns the raw signature bytes.
     *
     * @param toSign The source of the data to be signed
     * @return The raw bytes of the signature
     */
    byte[] sign(InputStream toSign);

    /**
     * Returns the id of the key that will be used for signing.
     *
     * @return The key id
     */
    @Input
    String getKeyId();
}
