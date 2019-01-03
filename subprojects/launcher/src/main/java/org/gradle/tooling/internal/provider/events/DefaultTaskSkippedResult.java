/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.provider.events;

import org.gradle.tooling.internal.protocol.events.InternalTaskSkippedResult;

public class DefaultTaskSkippedResult extends AbstractTaskResult implements InternalTaskSkippedResult {
    private final String skipMessage;

    public DefaultTaskSkippedResult(long startTime, long endTime, String skipMessage, boolean incremental) {
        super(startTime, endTime, "skipped", incremental, null);
        this.skipMessage = skipMessage;
    }

    @Override
    public String getSkipMessage() {
        return skipMessage;
    }
}
