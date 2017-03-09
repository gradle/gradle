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

package org.gradle.plugin.management.internal;

import org.gradle.api.Action;
import org.gradle.plugin.management.PluginResolveDetails;
import org.gradle.plugin.use.internal.InternalPluginRequest;

import java.util.LinkedHashSet;
import java.util.Set;

class PluginResolutionRules {

    private final Set<Action<? super PluginResolveDetails>> resolutionDetails = new LinkedHashSet<Action<? super PluginResolveDetails>>();

    public void add(Action<? super PluginResolveDetails> rule) {
        resolutionDetails.add(rule);
    }

    InternalPluginRequest resolveRequest(InternalPluginRequest pluginRequest) {
        for (Action<? super PluginResolveDetails> resolutionDetail : resolutionDetails) {
            DefaultPluginResolveDetails details = new DefaultPluginResolveDetails(pluginRequest);
            resolutionDetail.execute(details);
            pluginRequest = details.getTarget();
        }

        return pluginRequest;
    }

}
