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

import org.gradle.model.internal.core.ModelCreator;
import org.gradle.model.internal.core.ModelPath;

import java.util.Collection;

public class CreatorRuleBinder extends RuleBinder {
    private final ModelCreator creator;

    public CreatorRuleBinder(ModelCreator creator, ModelPath scope, Collection<RuleBinder> binders) {
        super(creator.getInputs(), creator.getDescriptor(), scope, binders);
        this.creator = creator;
    }

    public ModelCreator getCreator() {
        return creator;
    }
}
