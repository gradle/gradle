/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution;

/**
 * Action that only executes the delegate when configure on demand mode is 'on'
 *
 * by Szczepan Faber, created at: 1/8/13
 */
public class OnlyWhenConfigureOnDemand implements BuildConfigurationAction {

    private final BuildConfigurationAction delegate;

    public OnlyWhenConfigureOnDemand(BuildConfigurationAction delegate) {
        this.delegate = delegate;
    }

    public void configure(BuildExecutionContext context) {
        if (context.getGradle().getStartParameter().isConfigureOnDemand()) {
            delegate.configure(context);
        } else {
            context.proceed();
        }
    }
}