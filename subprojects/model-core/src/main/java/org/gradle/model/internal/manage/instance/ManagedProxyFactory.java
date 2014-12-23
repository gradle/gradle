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

package org.gradle.model.internal.manage.instance;

import org.gradle.internal.UncheckedException;

import java.lang.reflect.Constructor;

public class ManagedProxyFactory {

    private ManagedProxyClassGenerator proxyClassGenerator = new ManagedProxyClassGenerator();

    public <T> T createProxy(ModelElementState state, Class<T> managedType) {
        try {
            Class<? extends T> generatedClass = proxyClassGenerator.generate(managedType);
            Constructor<? extends T> constructor = generatedClass.getConstructor(ModelElementState.class);
            return constructor.newInstance(state);
        } catch (ReflectiveOperationException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

}
