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
package org.gradle.plugins.signing.type;

import org.gradle.plugins.signing.signatory.Signatory;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The type of signature.
 */
public interface SignatureType {

    /**
     * The file extension (without the leading dot) associated to this type of signature.
     */
    String getExtension();

    /**
     * Calculates the file where to store the signature of the given file to be signed.
     *
     * @param toSign The file to be signed
     * @return The file where to write the signature of the given file to be signed
     */
    File fileFor(File toSign);

    /**
     * Combines the extension of the given file with the expected signature extension.
     *
     * @see #getExtension()
     * @param toSign The file to be signed
     * @return The combined file extension (without the leading dot)
     */
    String combinedExtension(File toSign);

    /**
     * Signs the given file and returns the file where the signature has been written to.
     *
     * @param signatory The signatory
     * @param toSign The file to be signed
     * @return The file where the signature has been written to
     */
    File sign(Signatory signatory, File toSign);

    /**
     * Signs the data from the given InputStream and stores the signature in the given OutputStream.
     *
     * @param signatory The signatory
     * @param toSign The source of the data to be signed
     * @param destination Where the signature will be written to
     */
    void sign(Signatory signatory, InputStream toSign, OutputStream destination);
}
