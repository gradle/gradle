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


import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObject
import org.gradle.internal.UncheckedException

public abstract class AbstractPersistableConfigurationObject implements PersistableConfigurationObject {
    public void load(File inputFile) {
        try {
            InputStream inputStream = new FileInputStream(inputFile);
            try {
                load(inputStream);
            } finally {
                inputStream.close();
            }
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public void loadDefaults() {
        try {
            String defaultResourceName = getDefaultResourceName();
            InputStream inputStream = getClass().getResourceAsStream(defaultResourceName);
            if (inputStream == null) {
                throw new IllegalStateException(String.format("Failed to load default resource '%s' of persistable configuration object of type '%s' (resource not found)", defaultResourceName, getClass().getName()));
            }
            try {
                load(inputStream);
            } finally {
                inputStream.close();
            }
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public abstract void load(InputStream inputStream) throws Exception;

    public void store(File outputFile) {
        try {
            OutputStream outputStream = new FileOutputStream(outputFile);
            try {
                store(outputStream);
            } finally {
                outputStream.close();
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public abstract void store(OutputStream outputStream);

    protected abstract String getDefaultResourceName();
}
