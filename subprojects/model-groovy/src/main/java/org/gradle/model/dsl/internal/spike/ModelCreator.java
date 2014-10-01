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

package org.gradle.model.dsl.internal.spike;

import org.gradle.api.Transformer;

import java.util.Map;
import java.util.Set;

public class ModelCreator {

    private final Set<String> inputPaths;

    private final Transformer<Object, ? super Map<String, Object>> creator;
    public ModelCreator(Set<String> inputPaths, Transformer<Object, ? super Map<String, Object>> creator) {
        this.inputPaths = inputPaths;
        this.creator = creator;
    }

    public Object create(Map<String, Object> inputs) {
        return creator.transform(inputs);
    }

    public Set<String> getInputPaths() {
        return inputPaths;
    }
}
