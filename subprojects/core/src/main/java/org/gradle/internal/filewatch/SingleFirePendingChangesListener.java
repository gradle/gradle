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

package org.gradle.internal.filewatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleFirePendingChangesListener implements PendingChangesListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleFirePendingChangesListener.class);

    private final PendingChangesListener delegate;
    private boolean seenChanges;

    public SingleFirePendingChangesListener(PendingChangesListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onPendingChanges() {
        // Only fire once
        if (!seenChanges) {
            LOGGER.debug("notifying pending changes");
            delegate.onPendingChanges();
            seenChanges = true;
        } else {
            LOGGER.debug("already notified");
        }
    }
}
