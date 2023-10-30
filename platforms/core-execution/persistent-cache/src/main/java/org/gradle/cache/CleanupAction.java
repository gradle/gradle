/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.cache;

/**
 * An action that cleans up a {@link CleanableStore}.
 *
 * @see org.gradle.cache.internal.CompositeCleanupAction
 * @see CacheBuilder#withCleanup(CleanupAction)
 */
public interface CleanupAction {

    void clean(CleanableStore cleanableStore, CleanupProgressMonitor progressMonitor);

    CleanupAction NO_OP = new CleanupAction() {
        @Override
        public void clean(CleanableStore cleanableStore, CleanupProgressMonitor progressMonitor) {
            // no-op
        }
    };

}
