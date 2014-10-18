/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.plugins;

import org.gradle.api.Plugin;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;

public class PluginInspector {

    private final ModelRuleSourceDetector modelRuleSourceDetector;

    public PluginInspector(ModelRuleSourceDetector modelRuleSourceDetector) {
        this.modelRuleSourceDetector = modelRuleSourceDetector;
    }

    public PotentialPlugin inspect(Class<?> type) {
        boolean implementsInterface = Plugin.class.isAssignableFrom(type);
        boolean hasRules = this.modelRuleSourceDetector.hasModelSources(type);

        if (implementsInterface) {
            @SuppressWarnings("unchecked") Class<? extends Plugin<?>> asPluginImplType = (Class<? extends Plugin<?>>) type;
            if (hasRules) {
                return new PotentialHybridImperativeAndRulesPlugin(asPluginImplType);
            } else {
                return new PotentialImperativeClassPlugin(asPluginImplType);
            }
        } else if (hasRules) {
            return new PotentialPureRuleSourceClassPlugin(type);
        } else {
            return new PotentialUnknownTypePlugin(type);
        }
    }

}
