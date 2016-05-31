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

package org.gradle.internal.classloader;

import com.google.common.hash.HashCode;

/**
 * Represents the snapshot of given classpath
 */
public interface ClassPathSnapshot {
    boolean equals(Object other);
    int hashCode();

    /**
     * @return a hash for this classpath snapshot that is stronger than {@link #hashCode()} in
     * a way that it should, if possible, be independent from the file paths. Some implementations
     * may not be capable of doing this, but are encouraged to do so. Order is still important, but
     * location of files shouldn't matter.
     */
    HashCode getStrongHash();
}
