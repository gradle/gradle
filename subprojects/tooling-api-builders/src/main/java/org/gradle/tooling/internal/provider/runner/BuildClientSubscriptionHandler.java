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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.tooling.internal.provider.BuildClientSubscriptions;

import java.util.HashSet;
import java.util.Set;

public class BuildClientSubscriptionHandler {

    private Set<BuildClientSubscriptions> registeredClientSubscriptions = new HashSet<BuildClientSubscriptions>();

    void registerListenersForClientSubscriptions(BuildClientSubscriptions clientSubscriptions, GradleInternal gradle) {
        registeredClientSubscriptions.add(clientSubscriptions);
        BuildEventConsumer eventConsumer = gradle.getServices().get(BuildEventConsumer.class);
        if (clientSubscriptions.isSendTestProgressEvents()) {
            gradle.addListener(new ClientForwardingTestListener(eventConsumer, clientSubscriptions));
        }
        if (clientSubscriptions.isSendTaskProgressEvents()) {
            gradle.addListener(new ClientForwardingTaskListener(eventConsumer, clientSubscriptions));
        }
        if (clientSubscriptions.isSendBuildProgressEvents()) {
            gradle.addListener(new ClientForwardingBuildListener(eventConsumer));
        }
    }

    public void removeClientSubscriptions(BuildClientSubscriptions clientSubscriptions) {
        registeredClientSubscriptions.remove(clientSubscriptions);
    }

    public boolean hasClientSubscriptionsRegistered(BuildClientSubscriptions clientSubscriptions) {
        return registeredClientSubscriptions.contains(clientSubscriptions);
    }
}
