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

package org.gradle.model.internal.core;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.internal.Actions;
import org.gradle.internal.BiAction;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.NestedModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ThreadSafe
abstract public class ModelCreators {

    public static <T> Builder bridgedInstance(ModelReference<T> modelReference, T instance) {
        return unmanagedInstance(modelReference, Factories.constant(instance), Actions.doNothing());
    }

    public static <T> Builder unmanagedInstance(final ModelReference<T> modelReference, final Factory<? extends T> factory) {
        return unmanagedInstance(modelReference, factory, Actions.doNothing());
    }

    public static <T> Builder unmanagedInstance(final ModelReference<T> modelReference, final Factory<? extends T> factory, final Action<? super MutableModelNode> initializer) {
        return unmanagedInstanceOf(modelReference, new Transformer<T, MutableModelNode>() {
            @Override
            public T transform(MutableModelNode modelNode) {
                T t = factory.create();
                initializer.execute(modelNode);
                return t;
            }
        });
    }

    public static <T> Builder unmanagedInstanceOf(final ModelReference<T> modelReference, final Transformer<? extends T, ? super MutableModelNode> factory) {
        return of(modelReference.getPath(), new Action<MutableModelNode>() {
            @Override
            public void execute(MutableModelNode modelNode) {
                T t = factory.transform(modelNode);
                modelNode.setPrivateData(modelReference.getType(), t);
            }
        })
            .withProjection(UnmanagedModelProjection.of(modelReference.getType()));
    }

    public static Builder of(ModelPath path) {
        return new Builder(path);
    }

    public static Builder of(ModelPath path, NodeInitializer initializer) {
        return of(path)
            .action(ModelActionRole.Create, initializer)
            .withProjections(initializer.getProjections());
    }

    public static Builder of(ModelPath path, ModelReference<?> input, BiAction<? super MutableModelNode, ? super List<ModelView<?>>> initializer) {
        return of(path, Collections.singletonList(input), initializer);
    }

    public static Builder of(ModelPath path, List<? extends ModelReference<?>> inputs, BiAction<? super MutableModelNode, ? super List<ModelView<?>>> initializer) {
        return new Builder(path)
            .action(ModelActionRole.Create, inputs, initializer);
    }

    public static Builder of(ModelPath path, Action<? super MutableModelNode> initializer) {
        return new Builder(path)
            .action(ModelActionRole.Create, initializer);
    }

    public static <T> Builder of(final ModelReference<T> modelReference, final Factory<? extends T> factory) {
        return of(modelReference.getPath(), new Action<MutableModelNode>() {
            @Override
            public void execute(MutableModelNode modelNode) {
                T value = factory.create();
                modelNode.setPrivateData(modelReference.getType(), value);
            }
        });
    }

    @NotThreadSafe
    public static class Builder {
        private final ModelPath path;
        private final List<ModelProjection> projections = new ArrayList<ModelProjection>();
        private final ListMultimap<ModelActionRole, ModelAction> actions = ArrayListMultimap.create();
        private boolean ephemeral;
        private boolean hidden;

        private ModelRuleDescriptor modelRuleDescriptor;

        private Builder(ModelPath path) {
            this.path = path;
        }

        public Builder descriptor(String descriptor) {
            this.modelRuleDescriptor = new SimpleModelRuleDescriptor(descriptor);
            return this;
        }

        public Builder descriptor(ModelRuleDescriptor descriptor) {
            this.modelRuleDescriptor = descriptor;
            return this;
        }

        public Builder descriptor(ModelRuleDescriptor parent, ModelRuleDescriptor child) {
            this.modelRuleDescriptor = new NestedModelRuleDescriptor(parent, child);
            return this;
        }

        public Builder descriptor(ModelRuleDescriptor parent, String child) {
            this.modelRuleDescriptor = new NestedModelRuleDescriptor(parent, new SimpleModelRuleDescriptor(child));
            return this;
        }

        public Builder action(ModelActionRole role, ModelAction action) {
            this.actions.put(role, action);
            return this;
        }

        public Builder action(ModelActionRole role, final Action<? super MutableModelNode> initializer) {
            this.action(role, new BuilderModelAction() {
                @Override
                public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
                    initializer.execute(modelNode);
                }

                @Override
                public List<? extends ModelReference<?>> getInputs() {
                    return Collections.emptyList();
                }
            });
            return this;
        }

        public Builder action(ModelActionRole role, final NodeInitializer initializer) {
            this.action(role, new BuilderModelAction() {
                @Override
                public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
                    initializer.execute(modelNode, inputs);
                }

                @Override
                public List<? extends ModelReference<?>> getInputs() {
                    return initializer.getInputs();
                }
            });
            this.projections.addAll(initializer.getProjections());
            return this;
        }

        public Builder action(ModelActionRole role, ModelReference<?> input, BiAction<? super MutableModelNode, ? super List<ModelView<?>>> initializer) {
            this.action(role, Collections.singletonList(input), initializer);
            return this;
        }

        public Builder action(ModelActionRole role, final List<? extends ModelReference<?>> inputs, final BiAction<? super MutableModelNode, ? super List<ModelView<?>>> initializer) {
            this.action(role, new BuilderModelAction() {
                @Override
                public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
                    initializer.execute(modelNode, inputs);
                }

                @Override
                public List<? extends ModelReference<?>> getInputs() {
                    return inputs;
                }
            });
            return this;
        }

        // Callers must take care
        public Builder withProjection(ModelProjection projection) {
            projections.add(projection);
            return this;
        }

        public Builder withProjections(Iterable<? extends ModelProjection> projections) {
            for (ModelProjection projection : projections) {
                withProjection(projection);
            }
            return this;
        }

        public Builder hidden(boolean flag) {
            this.hidden = flag;
            return this;
        }

        public Builder ephemeral(boolean flag) {
            this.ephemeral = flag;
            return this;
        }

        @SuppressWarnings("unchecked")
        public ModelCreator build() {
            return new ProjectionBackedModelCreator(path, modelRuleDescriptor, ephemeral, hidden, projections, actions);
        }

        private abstract class BuilderModelAction implements ModelAction {
            @Override
            public ModelReference<?> getSubject() {
                return ModelReference.of(path);
            }

            @Override
            public ModelRuleDescriptor getDescriptor() {
                return modelRuleDescriptor;
            }
        }
    }

}
