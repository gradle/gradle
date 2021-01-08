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
package org.gradle.plugins.signing;

import org.gradle.plugins.signing.signatory.Signatory;
import org.gradle.plugins.signing.type.SignatureType;

/**
 * Specifies how objects will be signed.
 */
public interface SignatureSpec {

    /**
     * The signatory that will be performing the signing.
     *
     * @return the signatory, or {@code null} if none specified.
     */
    Signatory getSignatory();

    /**
     * Sets the signatory that will be signing the input.
     *
     * @param signatory The signatory
     */
    void setSignatory(Signatory signatory);

    /**
     * The signature representation that will be created.
     *
     * @return the signature type, or {@code null} if none specified.
     */
    SignatureType getSignatureType();

    /**
     * Sets the signature representation that the signatures will be produced as.
     *
     * @param type the signature type to use
     */
    void setSignatureType(SignatureType type);

    /**
     * Whether or not it is required that this signature be generated.
     *
     * A signature may not be able to be generated if a signatory and/or a signature type have not been specified. If it is required and cannot be generated, an exception will be thrown. Otherwise, it
     * will not be generated.
     *
     * @return Whether or not it is required that this signature be generated.
     */
    boolean isRequired();

    /**
     * Whether or not it is required that this signature be generated.
     *
     * @param required Whether or not it is required that this signature be generated.
     * @see #isRequired
     */
    void setRequired(boolean required);
}
