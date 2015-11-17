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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Incubating;
import org.gradle.api.Nullable;
import org.gradle.api.internal.AbstractBuildableModelElement;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.ModelMaps;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ModelInstantiationException;
import org.gradle.platform.base.internal.*;
import org.gradle.util.DeprecationLogger;

/**
 * Base class for custom binary implementations.
 * A custom implementation of {@link org.gradle.platform.base.BinarySpec} must extend this type.
 *
 * TODO at the moment leaking BinarySpecInternal here to generate lifecycleTask in
 * LanguageBasePlugin$createLifecycleTaskForBinary#createLifecycleTaskForBinary rule
 *
 */
@Incubating
// Needs to be here instead of the specific methods, because Java 6 and 7 will throw warnings otherwise
@SuppressWarnings("deprecation")
public class BaseBinarySpec extends AbstractBuildableModelElement implements BinarySpecInternal {
    private final DomainObjectSet<LanguageSourceSet> inputSourceSets = new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet.class);

    private static ThreadLocal<BinaryInfo> nextBinaryInfo = new ThreadLocal<BinaryInfo>();
    private final BinaryTasksCollection tasks;
    private final ComponentSpecInternal owner;
    private final String name;
    private final MutableModelNode modelNode;
    private final MutableModelNode sources;
    private final Class<? extends BinarySpec> publicType;

    private boolean disabled;

    public static <T extends BaseBinarySpec> T create(Class<? extends BinarySpec> publicType, Class<T> implementationType,
                                                      String name, MutableModelNode modelNode, @Nullable ComponentSpecInternal owner,
                                                      Instantiator instantiator, ITaskFactory taskFactory) {
        nextBinaryInfo.set(new BinaryInfo(name, publicType, implementationType, modelNode, owner, taskFactory, instantiator));
        try {
            try {
                return instantiator.newInstance(implementationType);
            } catch (ObjectInstantiationException e) {
                throw new ModelInstantiationException(String.format("Could not create binary of type %s", implementationType.getSimpleName()), e.getCause());
            }
        } finally {
            nextBinaryInfo.set(null);
        }
    }

    public BaseBinarySpec() {
        this(nextBinaryInfo.get());
    }

    private BaseBinarySpec(BinaryInfo info) {
        if (info == null) {
            throw new ModelInstantiationException("Direct instantiation of a BaseBinarySpec is not permitted. Use a BinaryTypeBuilder instead.");
        }
        this.owner = info.owner;
        this.name = info.name;
        this.publicType = info.publicType;
        this.modelNode = info.modelNode;
        this.tasks = info.instantiator.newInstance(DefaultBinaryTasksCollection.class, this, info.taskFactory);

        sources = ModelMaps.addModelMapNode(modelNode, LanguageSourceSet.class, "sources");
    }

    @Override
    public Class<? extends BinarySpec> getPublicType() {
        return publicType;
    }

    @Nullable
    public ComponentSpec getComponent() {
        return owner;
    }

    protected String getTypeName() {
        return publicType.getSimpleName();
    }

    @Override
    public String getProjectScopedName() {
        return owner == null ? name : owner.getName() + StringUtils.capitalize(name);
    }

    public String getDisplayName() {
        if (owner == null) {
            return String.format("%s '%s'", getTypeName(), name);
        } else {
            return String.format("%s '%s:%s'", getTypeName(), owner.getName(), name);
        }
    }

    public String getName() {
        return name;
    }

    @Override
    public void setBuildable(boolean buildable) {
        this.disabled = !buildable;
    }

    public final boolean isBuildable() {
        return getBuildAbility().isBuildable();
    }

    @Override
    public DomainObjectSet<LanguageSourceSet> getSource() {
        DeprecationLogger.nagUserOfReplacedProperty("source", "inputs");
        return getInputs();
    }

    public void sources(Action<? super ModelMap<LanguageSourceSet>> action) {
        action.execute(getSources());
    }

    @Override
    public DomainObjectSet<LanguageSourceSet> getInputs() {
        return inputSourceSets;
    }

    @Override
    public ModelMap<LanguageSourceSet> getSources() {
        return ModelMaps.asMutableView(sources, LanguageSourceSet.class, modelNode.toString() + ".getSources()");
    }

    public BinaryTasksCollection getTasks() {
        return tasks;
    }

    @Override
    public void tasks(Action<? super BinaryTasksCollection> action) {
        action.execute(tasks);
    }

    public boolean isLegacyBinary() {
        return false;
    }

    private static class BinaryInfo {
        private final String name;
        private final Class<? extends BinarySpec> publicType;
        private final Class<? extends BaseBinarySpec> implementationType;
        private final MutableModelNode modelNode;
        private final ComponentSpecInternal owner;
        private final ITaskFactory taskFactory;
        private final Instantiator instantiator;

        private BinaryInfo(String name, Class<? extends BinarySpec> publicType, Class<? extends BaseBinarySpec> implementationType, MutableModelNode modelNode, ComponentSpecInternal owner, ITaskFactory taskFactory, Instantiator instantiator) {
            this.name = name;
            this.publicType = publicType;
            this.implementationType = implementationType;
            this.modelNode = modelNode;
            this.owner = owner;
            this.taskFactory = taskFactory;
            this.instantiator = instantiator;
        }
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public final BinaryBuildAbility getBuildAbility() {
        if (disabled) {
            return new FixedBuildAbility(false);
        }
        return getBinaryBuildAbility();
    }

    protected BinaryBuildAbility getBinaryBuildAbility() {
        // Default behavior is to always be buildable.  Binary implementations should define what
        // criteria make them buildable or not.
        return new FixedBuildAbility(true);
    }
}
