/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.verification.model;

import com.google.common.collect.ImmutableList;

import java.util.List;

public enum ChecksumKind {
    md5("MD5"),
    sha1("SHA1"),
    sha256("SHA-256"),
    sha512("SHA-512");

    private static final List<ChecksumKind> SORTED_BY_SECURITY = ImmutableList.of(sha512, sha256, sha1, md5);
    private final String algorithm;

    ChecksumKind(String algorithm) {

        this.algorithm = algorithm;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public static List<ChecksumKind> mostSecureFirst() {
        return SORTED_BY_SECURITY;
    }
}
