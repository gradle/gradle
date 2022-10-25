/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.fingerprint;

// TODO Break this up between simple normalizers and Java classpath normalizers
//      The latter should be moved to :normalization-java
public enum InputNormalizer implements Normalizer {
    ABSOLUTE_PATH(false),
    RELATIVE_PATH(false),
    NAME_ONLY(false),
    IGNORE_PATH(true),
    RUNTIME_CLASSPATH(true),
    COMPILE_CLASSPATH(true);

    private final boolean ignoreDirectories;
    private final String description;

    InputNormalizer(boolean ignoreDirectories) {
        this.ignoreDirectories = ignoreDirectories;
        this.description = name().toLowerCase().replace('_', ' ');
    }

    @Override
    public boolean isIgnoreDirectories() {
        return ignoreDirectories;
    }

    @Override
    public String toString() {
        return description;
    }
}
