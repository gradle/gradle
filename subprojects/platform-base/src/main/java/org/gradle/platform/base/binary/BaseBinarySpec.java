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

import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.internal.AbstractBuildableComponentSpec;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.ModelMaps;
import org.gradle.model.internal.core.ModelRegistration;
import org.gradle.model.internal.core.ModelRegistrations;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.UnmanagedModelProjection;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ModelInstantiationException;
import org.gradle.platform.base.internal.BinaryBuildAbility;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.ComponentSpecIdentifier;
import org.gradle.platform.base.internal.DefaultBinaryNamingScheme;
import org.gradle.platform.base.internal.DefaultBinaryTasksCollection;
import org.gradle.platform.base.internal.FixedBuildAbility;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;

/**
 * Base class that may be used for custom {@link BinarySpec} implementations. However, it is generally better to use an
 * interface annotated with {@link org.gradle.model.Managed} and not use an implementation class at all.
 */
@Incubating
public class BaseBinarySpec extends AbstractBuildableComponentSpec implements BinarySpecInternal {
    private static final ModelType<BinaryTasksCollection> BINARY_TASKS_COLLECTION = ModelType.of(BinaryTasksCollection.class);
    private static final ModelType<LanguageSourceSet> LANGUAGE_SOURCE_SET_MODELTYPE = ModelType.of(LanguageSourceSet.class);

    private static final ThreadLocal<BinaryInfo> NEXT_BINARY_INFO = new ThreadLocal<BinaryInfo>();
    private final DomainObjectSet<LanguageSourceSet> inputSourceSets = new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet.class);
    private final BinaryTasksCollection tasks;
    private final MutableModelNode componentNode;
    private final MutableModelNode sources;
    private final Class<? extends BinarySpec> publicType;
    private BinaryNamingScheme namingScheme;
    private boolean disabled;

    public static <T extends BaseBinarySpec> T create(Class<? extends BinarySpec> publicType, Class<T> implementationType,
                                                      ComponentSpecIdentifier componentId, MutableModelNode modelNode, @Nullable MutableModelNode componentNode,
                                                      Instantiator instantiator, ITaskFactory taskFactory) {
        NEXT_BINARY_INFO.set(new BinaryInfo(componentId, publicType, modelNode, componentNode, taskFactory, instantiator));
        try {
            try {
                return DirectInstantiator.INSTANCE.newInstance(implementationType);
            } catch (ObjectInstantiationException e) {
                throw new ModelInstantiationException(String.format("Could not create binary of type %s", publicType.getSimpleName()), e.getCause());
            }
        } finally {
            NEXT_BINARY_INFO.set(null);
        }
    }

    public BaseBinarySpec() {
        this(NEXT_BINARY_INFO.get());
    }

    private BaseBinarySpec(BinaryInfo info) {
        super(validate(info).componentId, info.publicType);
        this.publicType = info.publicType;
        this.componentNode = info.componentNode;
        this.tasks = info.instantiator.newInstance(DefaultBinaryTasksCollection.class, this, info.taskFactory);

        MutableModelNode modelNode = info.modelNode;
        sources = ModelMaps.addModelMapNode(modelNode, LANGUAGE_SOURCE_SET_MODELTYPE, "sources");
        ModelRegistration itemRegistration = ModelRegistrations.of(modelNode.getPath().child("tasks"))
            .action(ModelActionRole.Create, new Action<MutableModelNode>() {
                @Override
                public void execute(MutableModelNode modelNode) {
                    modelNode.setPrivateData(BINARY_TASKS_COLLECTION, tasks);
                }
            })
            .withProjection(new UnmanagedModelProjection<BinaryTasksCollection>(BINARY_TASKS_COLLECTION))
            .descriptor(modelNode.getDescriptor())
            .build();
        modelNode.addLink(itemRegistration);

        namingScheme = DefaultBinaryNamingScheme
            .component(parentComponentName())
            .withBinaryName(getName())
            .withBinaryType(getTypeName());
    }

    private static BinaryInfo validate(BinaryInfo info) {
        if (info == null) {
            throw new ModelInstantiationException("Direct instantiation of a BaseBinarySpec is not permitted. Use a @ComponentType rule instead.");
        }
        return info;
    }

    @Nullable
    private String parentComponentName() {
        ComponentSpec component = getComponent();
        return component != null ? component.getName() : null;
    }

    @Override
    public LibraryBinaryIdentifier getId() {
        // TODO: This can throw a NPE: will need an identifier for a variant without an owning component
        ComponentSpec component = getComponent();
        return new DefaultLibraryBinaryIdentifier(component.getProjectPath(), component.getName(), getName());
    }

    @Override
    public Class<? extends BinarySpec> getPublicType() {
        return publicType;
    }

    @Override
    @Nullable
    public ComponentSpec getComponent() {
        return getComponentAs(ComponentSpec.class);
    }

    @Nullable
    protected <T extends ComponentSpec> T getComponentAs(Class<T> componentType) {
        if (componentNode == null) {
            return null;
        }
        ModelType<T> modelType = ModelType.of(componentType);
        return componentNode.canBeViewedAs(modelType)
            ? componentNode.asImmutable(modelType, componentNode.getDescriptor()).getInstance()
            : null;
    }

    @Override
    public String getProjectScopedName() {
        return getIdentifier().getProjectScopedName();
    }

    @Override
    public void setBuildable(boolean buildable) {
        this.disabled = !buildable;
    }

    @Override
    public final boolean isBuildable() {
        return getBuildAbility().isBuildable();
    }

    @Override
    public DomainObjectSet<LanguageSourceSet> getInputs() {
        return inputSourceSets;
    }

    @Override
    public ModelMap<LanguageSourceSet> getSources() {
        return ModelMaps.toView(sources, LANGUAGE_SOURCE_SET_MODELTYPE);
    }

    @Override
    public BinaryTasksCollection getTasks() {
        return tasks;
    }

    @Override
    public boolean isLegacyBinary() {
        return false;
    }

    @Override
    public BinaryNamingScheme getNamingScheme() {
        return namingScheme;
    }

    @Override
    public void setNamingScheme(BinaryNamingScheme namingScheme) {
        this.namingScheme = namingScheme;
    }

    @Override
    public boolean hasCodependentSources() {
        return false;
    }

    private static class BinaryInfo {
        private final Class<? extends BinarySpec> publicType;
        private final MutableModelNode modelNode;
        private final MutableModelNode componentNode;
        private final ITaskFactory taskFactory;
        private final Instantiator instantiator;
        private final ComponentSpecIdentifier componentId;

        private BinaryInfo(ComponentSpecIdentifier componentId, Class<? extends BinarySpec> publicType, MutableModelNode modelNode, MutableModelNode componentNode, ITaskFactory taskFactory, Instantiator instantiator) {
            this.componentId = componentId;
            this.publicType = publicType;
            this.modelNode = modelNode;
            this.componentNode = componentNode;
            this.taskFactory = taskFactory;
            this.instantiator = instantiator;
        }
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

    public static void replaceSingleDirectory(Set<File> dirs, File dir) {
        switch (dirs.size()) {
            case 0:
                dirs.add(dir);
                break;
            case 1:
                dirs.clear();
                dirs.add(dir);
                break;
            default:
                throw new IllegalStateException("Can't replace multiple directories.");
        }
    }

}
