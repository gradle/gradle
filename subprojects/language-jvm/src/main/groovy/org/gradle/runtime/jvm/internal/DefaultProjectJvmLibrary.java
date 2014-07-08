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

package org.gradle.runtime.jvm.internal;


import org.gradle.api.DomainObjectSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetContainer;
import org.gradle.runtime.jvm.ProjectJvmLibrary;

public class DefaultProjectJvmLibrary implements ProjectJvmLibrary {
    private final LanguageSourceSetContainer sourceSets = new LanguageSourceSetContainer();
    private final String name;

    public DefaultProjectJvmLibrary(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public DomainObjectSet<LanguageSourceSet> getSource() {
        return sourceSets;
    }

    public void source(Object sources) {
        sourceSets.source(sources);
    }
}
