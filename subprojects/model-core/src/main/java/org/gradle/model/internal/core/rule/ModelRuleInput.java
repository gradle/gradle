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

package org.gradle.model.internal.core.rule;

import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelView;

public class ModelRuleInput<T> {

    private final ModelPath path;
    private final ModelView<T> view;

    public ModelRuleInput(ModelPath path, ModelView<T> view) {
        this.path = path;
        this.view = view;
    }

    public static <T> ModelRuleInput<T> of(ModelPath path, ModelView<T> view) {
        return new ModelRuleInput<T>(path, view);
    }

    public ModelPath getPath() {
        return path;
    }

    public ModelView<T> getView() {
        return view;
    }

    @Override
    public String toString() {
        return "ModelRuleInput{path=" + path + ", view=" + view + '}';
    }

}
