/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.base.internal.model;

import com.google.common.base.Optional;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultFunctionalSourceSet;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;

public class FunctionalSourceSetNodeInitializer implements NodeInitializer {
    private final String name;
    private final Instantiator instantiator;
    private final ProjectSourceSet projectSourceSet;

    public FunctionalSourceSetNodeInitializer(String name, Instantiator instantiator, final ProjectSourceSet projectSourceSet) {
        this.name = name;
        this.instantiator = instantiator;
        this.projectSourceSet = projectSourceSet;
    }

    @Override
    public List<? extends ModelReference<?>> getInputs() {
        return Collections.emptyList();
    }

    @Override
    public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
        DefaultFunctionalSourceSet defaultFunctionalSourceSet = new DefaultFunctionalSourceSet(name, instantiator, projectSourceSet);
        modelNode.setPrivateData(DefaultFunctionalSourceSet.class, defaultFunctionalSourceSet);
    }

    @Override
    public List<? extends ModelProjection> getProjections() {
        return Collections.singletonList(new FunctionalSourceSetProjection<FunctionalSourceSet>(ModelType.of(FunctionalSourceSet.class)));
    }

    static class FunctionalSourceSetProjection<M> extends UnmanagedModelProjection<M> {
        public FunctionalSourceSetProjection(ModelType<M> type) {
            super(type);
        }

        @Override
        public Optional<String> getValueDescription(MutableModelNode modelNodeInternal) {
            return Optional.of(String.format("source set '%s'", modelNodeInternal.getPath().toString()));
        }
    }
}
