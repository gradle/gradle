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

import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.internal.DefaultFunctionalSourceSet;
import org.gradle.model.internal.core.*;

import java.util.Collections;
import java.util.List;

public class FunctionalSourceSetModelInitializer implements NodeInitializer {

    @Override
    public List<? extends ModelReference<?>> getInputs() {
        return Collections.emptyList();
    }

    @Override
    public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
        DefaultFunctionalSourceSet defaultFunctionalSourceSet = new DefaultFunctionalSourceSet(null, null, null);
        modelNode.setPrivateData(DefaultFunctionalSourceSet.class, defaultFunctionalSourceSet);
    }

    @Override
    public List<? extends ModelProjection> getProjections() {
        return Collections.singletonList(UnmanagedModelProjection.of(FunctionalSourceSet.class));
    }
}
