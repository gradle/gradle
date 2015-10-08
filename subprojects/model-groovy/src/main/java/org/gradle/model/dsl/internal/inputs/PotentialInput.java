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

package org.gradle.model.dsl.internal.inputs;

import groovy.lang.GroovySystem;
import org.gradle.api.Transformer;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelView;

import java.util.List;

public class PotentialInput {

    private final Transformer<?, ModelView<?>> extractor;
    private final String consumerPath;
    private final int inputIndex;
    private final boolean absolute;

    private PotentialInput(String consumerPath, int inputIndex, boolean absolute, Transformer<?, ModelView<?>> extractor) {
        this.inputIndex = inputIndex;
        this.absolute = absolute;
        this.extractor = extractor;
        this.consumerPath = consumerPath;
    }

    public boolean isAbsolute() {
        return absolute;
    }

    public String getConsumerPath() {
        return consumerPath;
    }

    public Object get(List<ModelView<?>> views) {
        if (inputIndex == -1) {
            // internal error
            throw new IllegalStateException("potential input '" + consumerPath + "' did not bind");
        }
        return extractor.transform(views.get(inputIndex));
    }

    public static PotentialInput absoluteInput(String consumerPath, int indexPath) {
        return new PotentialInput(consumerPath, indexPath, true, new Transformer<Object, ModelView<?>>() {
            @Override
            public Object transform(ModelView<?> modelView) {
                return modelView.getInstance();
            }
        });
    }

    public static PotentialInput relativeInput(final ModelPath consumerPath, int indexPath) {
        return new PotentialInput(consumerPath.toString(), indexPath, false, new Transformer<Object, ModelView<?>>() {
            @Override
            public Object transform(ModelView<?> modelView) {
                Object object = modelView.getInstance();
                List<String> pathComponents = consumerPath.getComponents();
                for (String pathComponent : pathComponents.subList(1, pathComponents.size())) {
                    object = GroovySystem.getMetaClassRegistry().getMetaClass(object.getClass()).getProperty(object, pathComponent);
                }
                return object;
            }
        });
    }

}
