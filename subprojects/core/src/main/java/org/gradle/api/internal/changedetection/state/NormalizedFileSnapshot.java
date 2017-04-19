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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.internal.changedetection.resources.results.NormalizedSnapshot;

/**
 * An immutable snapshot of some aspects of a file's metadata and content.
 *
 * Should implement {@link #equals(Object)} and {@link #hashCode()} to compare these aspects.
 */
public interface NormalizedFileSnapshot extends NormalizedSnapshot {
    /*
     * `FileContentSnapshot` is only required to check if a file in the output
     * directory changed during task execution (timestamp of content) to
     * determine if it is part of the output of the task. We should probably
     * get rid of this two and only keep the hash or always keep the hash and the timestamp
     * separately.
     */
    FileContentSnapshot getSnapshot();
}
