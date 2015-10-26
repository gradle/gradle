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

import com.google.common.base.Joiner;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.internal.rules.AddOnlyRuleAwarePolymorphicDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.registry.LanguageRegistration;
import org.gradle.language.base.internal.registry.LanguageRegistry;

import java.io.File;
import java.util.Collections;

import static com.google.common.base.Strings.emptyToNull;

public class DefaultFunctionalSourceSet extends AddOnlyRuleAwarePolymorphicDomainObjectContainer<LanguageSourceSet> implements FunctionalSourceSet {
    private final String name;
    private final LanguageRegistry languageRegistry;
    private final File baseDir;

    public DefaultFunctionalSourceSet(String name, Instantiator instantiator, final ProjectSourceSet projectSourceSet, LanguageRegistry languageRegistry, File baseDir) {
        super(LanguageSourceSet.class, instantiator);
        this.name = name;
        this.languageRegistry = languageRegistry;
        this.baseDir = baseDir;
        whenObjectAdded(new Action<LanguageSourceSet>() {
            public void execute(LanguageSourceSet languageSourceSet) {
                projectSourceSet.add(languageSourceSet);
            }
        });
    }

    @Override
    protected <U extends LanguageSourceSet> U doCreate(String name, Class<U> type) {
        U languageSourceSet = createLanguageSourceSet(name, type);
        initializeDefaultSrcDir(name, languageSourceSet);
        return languageSourceSet;
    }

    private <U extends LanguageSourceSet> void initializeDefaultSrcDir(String name, U languageSourceSet) {
        String defaultSourceDir = calculateDefaultPath(name, languageSourceSet);
        languageSourceSet.getSource().setSrcDirs(Collections.singletonList(defaultSourceDir));
    }

    private <U extends LanguageSourceSet> U createLanguageSourceSet(String name, Class<U> type) {
        NamedDomainObjectFactory<? extends LanguageSourceSet> sourceSetFactory = findSourceSetFactory(type);
        return type.cast(sourceSetFactory.create(name));
    }

    @Override
    public <U extends LanguageSourceSet> U create(String name, Class<U> type, Action<? super U> configuration) {
        U languageSourceSet = createLanguageSourceSet(name, type);
        if (configuration != null) {
            configuration.execute(languageSourceSet);
        } else {
            initializeDefaultSrcDir(name, languageSourceSet);
        }
        add(languageSourceSet);
        return languageSourceSet;
    }

    private String calculateDefaultPath(String name, LanguageSourceSet languageSourceSet) {
        return Joiner.on(File.separator).skipNulls().join(baseDir.getPath(), emptyToNull(languageSourceSet.getSourceDirConvention()), emptyToNull(languageSourceSet.getParentName()), emptyToNull(name));
    }

    private <U extends LanguageSourceSet> NamedDomainObjectFactory<? extends LanguageSourceSet> findSourceSetFactory(Class<U> type) {
        for (LanguageRegistration<?> languageRegistration : languageRegistry) {
            Class<? extends LanguageSourceSet> sourceSetType = languageRegistration.getSourceSetType();
            if (type.equals(sourceSetType)) {
                return languageRegistration.getSourceSetFactory(getName());
            }
        }
        return null;
    }


    @Override
    public String toString() {
        return String.format("source set '%s'", name);
    }

    public String getName() {
        return name;
    }
}
