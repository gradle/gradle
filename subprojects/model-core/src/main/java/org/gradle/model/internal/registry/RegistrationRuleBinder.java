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

package org.gradle.model.internal.registry;

import org.gradle.model.internal.core.ModelRegistration;

import java.util.Collection;
import java.util.List;

class RegistrationRuleBinder extends RuleBinder {
    private final ModelRegistration registration;

    public RegistrationRuleBinder(ModelRegistration registration, BindingPredicate subject, List<BindingPredicate> inputs, Collection<RuleBinder> binders) {
        super(subject, inputs, registration.getDescriptor(), binders);
        this.registration = registration;
    }

    public ModelRegistration getRegistration() {
        return registration;
    }
}
