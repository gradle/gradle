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

package org.gradle.model.internal.listener;

import org.gradle.model.ModelPath;
import org.gradle.model.internal.ModelCreationListener;

public class ChildCreationListener implements ModelCreationListener {

    private final ModelPath parentPath;
    private final Class<?> targetType;
    private final ModelCreationListener delegate;

    public ChildCreationListener(String parentPath, Class<?> targetType, ModelCreationListener delegate) {
        this.delegate = delegate;
        this.parentPath = ModelPath.path(parentPath);
        this.targetType = targetType;
    }

    public boolean onCreate(ModelPath path, Class<?> type) {
        if (path.isDirectChild(parentPath) && targetType.isAssignableFrom(type)) {
            delegate.onCreate(path, type);
        }

        return false;
    }
}
