/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.changedetection.digest;

import org.gradle.api.GradleException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Creates MessageDigest objects for the SHA algorithm.
 *
 * @author Tom Eyckmans
 */
class ShaDigesterFactory implements DigesterFactory {
    private static final String SHA_ALGORITHM = "SHA-1";

    /**
     * Calls {@see MessageDigest.getInstance} for algorithm 'SHA'.
     *  
     * @return The created MessageDigest object.
     */
    public MessageDigest createDigester() {
        try {
            return MessageDigest.getInstance(SHA_ALGORITHM);
        }
        catch ( NoSuchAlgorithmException e) {
            throw new GradleException(SHA_ALGORITHM + " algorithm not supported!", e);
        }
    }
}
