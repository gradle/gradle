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

package org.gradle.model.internal.inspect;

import org.gradle.model.internal.core.*;

import java.util.List;

public class DefaultModelRuleSourceApplicator implements ModelRuleSourceApplicator {

    private final ModelRuleSourceDetector modelRuleSourceDetector;
    private final ModelRuleInspector inspector;

    public DefaultModelRuleSourceApplicator(ModelRuleSourceDetector modelRuleSourceDetector, ModelRuleInspector inspector) {
        this.modelRuleSourceDetector = modelRuleSourceDetector;
        this.inspector = inspector;
    }

    @Override
    public void apply(Class<?> ruleSource, ModelPath scope, ModelRegistrar modelRegistrar, PluginClassApplicator pluginClassApplicator) {
        for (Class<?> source : modelRuleSourceDetector.getDeclaredSources(ruleSource)) {
            List<ModelRuleRegistration> registrations = inspector.inspect(source);
            for (ModelRuleRegistration registration : registrations) {
                for (Class<?> dependency : registration.getRuleDependencies()) {
                    pluginClassApplicator.apply(dependency);
                }
                // TODO catch “strange” exceptions thrown here and wrap with some context on the rule being registered
                // If the thrown exception doesn't provide any “model rule” context, it will be more or less impossible for a user
                // to work out what happened because the stack trace won't reveal any info about which rule was being registered.
                // However, a “wrap everything” strategy doesn't quite work because the thrown exception may already have enough context
                // and do a better job of explaining what went wrong than what we can do at this level.
                registration.applyTo(modelRegistrar, scope);
            }
        }
    }
}
