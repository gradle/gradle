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


import org.gradle.util.UncheckedException;


public abstract class PropertiesPersistableConfigurationObject extends AbstractPersistableConfigurationObject {
    private Properties properties;

    @Override
    public void load(InputStream inputStream) throws Exception {
        properties = new Properties();
        properties.load(inputStream);
        load(properties);
    }

    @Override
    public void store(OutputStream outputStream) {
        store(properties);
        try {
            properties.store(outputStream, "");
        } catch (IOException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    protected abstract void store(Properties properties);

    protected abstract void load(Properties properties);
}
