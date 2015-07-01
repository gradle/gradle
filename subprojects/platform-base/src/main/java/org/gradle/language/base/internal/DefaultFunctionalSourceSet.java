/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.base.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.rules.AddOnlyRuleAwarePolymorphicDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;

public class DefaultFunctionalSourceSet extends AddOnlyRuleAwarePolymorphicDomainObjectContainer<LanguageSourceSet> implements FunctionalSourceSet {
    private final String name;

    public DefaultFunctionalSourceSet(String name, Instantiator instantiator, final ProjectSourceSet projectSourceSet) {
        super(LanguageSourceSet.class, instantiator);
        this.name = name;
        whenObjectAdded(new Action<LanguageSourceSet>() {
            public void execute(LanguageSourceSet languageSourceSet) {
                projectSourceSet.add(languageSourceSet);
            }
        });
    }

    @Override
    public String toString() {
        return String.format("source set '%s'", name);
    }

    public String getName() {
        return name;
    }
}
