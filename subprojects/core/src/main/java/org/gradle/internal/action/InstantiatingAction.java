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

public class InstantiatingAction<DETAILS> implements Action<DETAILS> {
    private final ConfigurableRule<DETAILS> rule;
    private final Instantiator instantiator;
    private final ExceptionHandler<DETAILS> exceptionHandler;

    public InstantiatingAction(ConfigurableRule<DETAILS> rule, Instantiator instantiator, ExceptionHandler<DETAILS> exceptionHandler) {
        this.rule = rule;
        this.instantiator = instantiator;
        this.exceptionHandler = exceptionHandler;
    }

    public InstantiatingAction<DETAILS> withInstantiator(Instantiator instantiator) {
        return new InstantiatingAction<DETAILS>(rule, instantiator, exceptionHandler);
    }

    @Override
    public void execute(DETAILS target) {
        try {
            Action<DETAILS> instance = instantiator.newInstance(rule.getRuleClass(), rule.getRuleParams().isolate());
            instance.execute(target);
        } catch (Throwable t) {
            exceptionHandler.handleException(target, t);
        }
    }

    public ConfigurableRule<DETAILS> getRule() {
        return rule;
    }

    public Instantiator getInstantiator() {
        return instantiator;
    }

    public interface ExceptionHandler<U> {
        void handleException(U target, Throwable throwable);
    }
}
