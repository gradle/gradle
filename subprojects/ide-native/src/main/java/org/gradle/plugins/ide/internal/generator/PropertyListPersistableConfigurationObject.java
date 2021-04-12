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

package org.gradle.plugins.ide.internal.generator;

import com.dd.plist.NSObject;
import com.dd.plist.PropertyListParser;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.internal.PropertyListTransformer;

import java.io.InputStream;
import java.io.OutputStream;

import static org.gradle.util.internal.ConfigureUtil.configureUsing;

public abstract class PropertyListPersistableConfigurationObject<T extends NSObject> extends AbstractPersistableConfigurationObject {
    private final Class<T> clazz;
    private final PropertyListTransformer<T> transformer;
    private T rootObject;

    protected PropertyListPersistableConfigurationObject(Class<T> clazz, PropertyListTransformer<T> transformer) {
        this.clazz = clazz;
        this.transformer = transformer;
    }

    protected abstract T newRootObject();

    @Override
    public void load(InputStream inputStream) throws Exception {
        rootObject = clazz.cast(PropertyListParser.parse(inputStream));
        if (rootObject == null) {
            rootObject = newRootObject();
        }
        load(rootObject);
    }

    @Override
    public void store(OutputStream outputStream) {
        store(rootObject);
        transformer.transform(rootObject, outputStream);
    }

    protected abstract void store(T rootObject);

    protected abstract void load(T rootObject);

    public void transformAction(Closure<?> action) {
        transformAction(configureUsing(action));
    }

    public void transformAction(Action<? super T> action) {
        transformer.addAction(action);
    }
}
