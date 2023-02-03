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

package org.gradle.internal.snapshot;

/**
 * The case sensitivity of a file system.
 *
 * Note that the method for actually comparing paths with a case sensitivity are in {@link PathUtil} instead of being on this enum,
 * since it seems that the methods can be better inlined by the JIT compiler if they are static.
 */
public enum CaseSensitivity {
    CASE_SENSITIVE,
    CASE_INSENSITIVE
}
