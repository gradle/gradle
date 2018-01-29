/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.caching.internal.version2;

import java.io.IOException;

public interface BuildCacheStoreCommandV2 {

    /**
     * Called at-most-once to initiate writing the artifact to the output stream.
     *
     * The output stream will be closed by this method.
     */
    Result store() throws IOException;

    interface Result {

        /**
         * The number of entries in the stored artifact.
         *
         * This is used as a rough metric of the complexity of the archive for processing
         * (in conjunction with the archive size).
         *
         * The meaning of “entry” is intentionally loose.
         */
        long getArtifactEntryCount();
    }
}
