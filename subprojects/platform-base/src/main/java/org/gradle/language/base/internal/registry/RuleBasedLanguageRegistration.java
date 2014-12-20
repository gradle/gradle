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

package org.gradle.language.base.internal.registry;

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.TransformationFileType;

import java.util.Map;

public class RuleBasedLanguageRegistration<T extends BaseLanguageSourceSet> implements LanguageRegistration<T> {

    private final String name;
    private final Class<T> sourceSetType;
    private final Class<? extends T> sourceSetImplementation;
    private Instantiator instantiator;

    public RuleBasedLanguageRegistration(String name, Class<T> sourceSetType, Class<? extends T> sourceSetImplementation, Instantiator instantiator) {
        this.name = name;
        this.sourceSetType = sourceSetType;
        this.sourceSetImplementation = sourceSetImplementation;
        this.instantiator = instantiator;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Class<T> getSourceSetType() {
        return sourceSetType;
    }

    @Override
    public Class<? extends T> getSourceSetImplementation() {
        return sourceSetImplementation;
    }

    @Override
    public Map<String, Class<?>> getBinaryTools() {
        return null;
    }

    @Override
    public Class<? extends TransformationFileType> getOutputType() {
        return null;
    }

    @Override
    public SourceTransformTaskConfig getTransformTask() {
        return null;
    }

    @Override
    public boolean applyToBinary(BinarySpec binary) {
        return false;
    }

    @Override
    public NamedDomainObjectFactory<? extends T> getSourceSetFactory(final String parentName, final FileResolver fileResolver) {
        return new NamedDomainObjectFactory<T>(){
            @Override
            public T create(String name) {
                return BaseLanguageSourceSet.create(sourceSetImplementation, name, parentName, fileResolver, instantiator);
            }
        };
    }
}
