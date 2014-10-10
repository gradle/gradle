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

package org.gradle.platform.base.binary;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.Incubating;
import org.gradle.api.internal.AbstractBuildableModelElement;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetContainer;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.ModelInstantiationException;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.DefaultBinaryTasksCollection;

/**
 * Base class for custom binary implementations.
 * A custom implementation of {@link org.gradle.platform.base.BinarySpec} must extend this type.
 *
 * TODO at the moment leaking BinarySpecInternal here to generate lifecycleTask in
 * LanguageBasePlugin$createLifecycleTaskForBinary#createLifecycleTaskForBinary rule
 *
 */
@Incubating
public abstract class BaseBinarySpec extends AbstractBuildableModelElement implements BinarySpecInternal {
    private final LanguageSourceSetContainer sourceSets = new LanguageSourceSetContainer();
    private static ThreadLocal<BinaryInfo> nextBinaryInfo = new ThreadLocal<BinaryInfo>();
    private final BinaryTasksCollection tasks = new DefaultBinaryTasksCollection(this);

    private final BinaryNamingScheme namingScheme;
    private final String typeName;

    public static <T extends BaseBinarySpec> T create(Class<T> type, BinaryNamingScheme namingScheme, Instantiator instantiator) {
        if (type.equals(BaseBinarySpec.class)) {
            throw new ModelInstantiationException("Cannot create instance of abstract class BaseBinarySpec.");
        }
        nextBinaryInfo.set(new BinaryInfo(namingScheme, type.getSimpleName()));
        try {
            try {
                return instantiator.newInstance(type);
            } catch (ObjectInstantiationException e) {
                throw new ModelInstantiationException(String.format("Could not create binary of type %s", type.getSimpleName()), e.getCause());
            }
        } finally {
            nextBinaryInfo.set(null);
        }
    }

    protected BaseBinarySpec() {
        this(nextBinaryInfo.get());
    }

    private BaseBinarySpec(BinaryInfo info) {
        if (info == null) {
            throw new ModelInstantiationException("Direct instantiation of a BaseBinarySpec is not permitted. Use a BinaryTypeBuilder instead.");
        }
        this.typeName = info.typeName;
        this.namingScheme = info.namingScheme;
    }

    public String getDisplayName() {
        return String.format("%s: '%s'", typeName, getName());
    }

    public boolean isBuildable() {
        return true;
    }

    public DomainObjectSet<LanguageSourceSet> getSource() {
        return sourceSets;
    }

    public void source(Object source) {
        sourceSets.source(source);
    }

    public BinaryTasksCollection getTasks() {
       return tasks;
    }

    public String getName() {
        return namingScheme.getLifecycleTaskName();
    }

    public BinaryNamingScheme getNamingScheme() {
        return namingScheme;
    }

    public boolean isLegacyBinary() {
        return false;
    }

    private static class BinaryInfo {
        final BinaryNamingScheme namingScheme;
        final String typeName;

        private BinaryInfo(BinaryNamingScheme namingScheme,
                           String typeName) {
            this.namingScheme = namingScheme;
            this.typeName = typeName;
        }
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
