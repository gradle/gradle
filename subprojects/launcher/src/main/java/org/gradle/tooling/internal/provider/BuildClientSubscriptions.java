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

package org.gradle.tooling.internal.provider;

import java.io.Serializable;

/**
 * Provides information about what events the build client is interested in.
 */
public class BuildClientSubscriptions implements Serializable {

    private final boolean sendTestProgressEvents;
    private final boolean sendTaskProgressEvents;
    private final boolean sendBuildProgressEvents;

    public BuildClientSubscriptions(boolean sendTestProgressEvents, boolean sendTaskProgressEvents, boolean sendBuildProgressEvents) {
        this.sendTestProgressEvents = sendTestProgressEvents;
        this.sendTaskProgressEvents = sendTaskProgressEvents;
        this.sendBuildProgressEvents = sendBuildProgressEvents;
    }

    public boolean isSendTestProgressEvents() {
        return sendTestProgressEvents;
    }

    public boolean isSendTaskProgressEvents() {
        return sendTaskProgressEvents;
    }

    public boolean isSendBuildProgressEvents() {
        return sendBuildProgressEvents;
    }

    public boolean isSendAnyProgressEvents() {
        return sendTestProgressEvents || sendTaskProgressEvents || sendBuildProgressEvents;
    }

}
