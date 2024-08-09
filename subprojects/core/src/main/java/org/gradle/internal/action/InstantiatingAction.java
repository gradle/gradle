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

package org.gradle.internal.action;

import org.gradle.api.Action;
import org.gradle.internal.reflect.Instantiator;

import java.util.ArrayList;
import java.util.List;

public class InstantiatingAction<DETAILS> implements Action<DETAILS> {
    private final ConfigurableRules<DETAILS> rules;
    private final Instantiator instantiator;
    private final ExceptionHandler<DETAILS> exceptionHandler;
    private final List<Action<DETAILS>> reusableRules;

    public InstantiatingAction(
        ConfigurableRules<DETAILS> rules,
        Instantiator instantiator,
        ExceptionHandler<DETAILS> exceptionHandler,
        boolean cacheRules
    ) {
        this.rules = rules;
        this.instantiator = instantiator;
        this.exceptionHandler = exceptionHandler;

        if (cacheRules) {
            this.reusableRules = new ArrayList<>(rules.getConfigurableRules().size());
        } else {
            this.reusableRules = null;
        }
    }

    public InstantiatingAction<DETAILS> withInstantiator(Instantiator instantiator) {
        return new InstantiatingAction<>(rules, instantiator, exceptionHandler, reusableRules != null);
    }

    @Override
    public void execute(DETAILS target) {
        List<ConfigurableRule<DETAILS>> configurableRules = rules.getConfigurableRules();
        for (int i = 0; i < configurableRules.size(); i++) {
            ConfigurableRule<DETAILS> rule = configurableRules.get(i);
            try {
                if (reusableRules == null) {
                    instantiateRule(rule).execute(target);
                } else {
                    Action<DETAILS> instance;
                    if (i < reusableRules.size()) {
                        instance = reusableRules.get(i);
                    } else {
                        instance = instantiateRule(rule);
                        reusableRules.add(instance);
                    }
                    instance.execute(target);
                }
            } catch (Throwable t) {
                exceptionHandler.handleException(target, t);
            }
        }
    }

    private Action<DETAILS> instantiateRule(ConfigurableRule<DETAILS> rule) {
        return instantiator.newInstance(rule.getRuleClass(), rule.getRuleParams().isolate());
    }

    public ConfigurableRules<DETAILS> getRules() {
        return rules;
    }

    public Instantiator getInstantiator() {
        return instantiator;
    }

    public interface ExceptionHandler<U> {
        void handleException(U target, Throwable throwable);
    }
}
