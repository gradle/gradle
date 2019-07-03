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

package org.gradle.internal.file;

public enum FingerprintFileType {
    RegularFile,
    Directory,
    Missing;

    public static FingerprintFileType of(SnapshotFileType type) {
        switch (type) {
            case RegularFile:
                return RegularFile;
            case Directory:
                return Directory;
            case Missing:
                return Missing;
            default:
                throw new IllegalArgumentException(String.format("Unknown type: %s", type));
        }
    }
}
