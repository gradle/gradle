/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.provider;

import com.google.common.collect.ImmutableList;
import org.gradle.api.provider.ListProperty;

import java.util.Collection;
import java.util.List;

public class DefaultListProperty<T> extends AbstractCollectionProperty<T, List<T>> implements ListProperty<T> {
    public DefaultListProperty(Class<T> elementType) {
        super(List.class);
    }

    @Override
    protected List<T> fromValue(Collection<T> values) {
        return ImmutableList.copyOf(values);
    }

}
