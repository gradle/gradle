/*
 * Copyright 2010 the original author or authors.
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

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.internal.PropertiesTransformer;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import static org.gradle.util.internal.ConfigureUtil.configureUsing;

public abstract class PropertiesPersistableConfigurationObject extends AbstractPersistableConfigurationObject {

    private final PropertiesTransformer transformer;
    private Properties properties;

    protected PropertiesPersistableConfigurationObject(PropertiesTransformer transformer) {
        this.transformer = transformer;
    }

    @Override
    public void load(InputStream inputStream) throws Exception {
        properties = new Properties();
        properties.load(inputStream);
        load(properties);
    }

    @Override
    public void store(OutputStream outputStream) {
        store(properties);
        transformer.transform(properties, outputStream);
    }

    protected abstract void store(Properties properties);

    protected abstract void load(Properties properties);

    public void transformAction(@DelegatesTo(Properties.class) Closure action) {
        transformAction(configureUsing(action));
    }

    /**
     * @param action transform action
     * @since 3.5
     */
    public void transformAction(Action<? super Properties> action) {
        transformer.addAction(action);
    }
}
