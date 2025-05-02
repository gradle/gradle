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
package org.gradle.internal.service;

import java.lang.reflect.Method;

import static org.gradle.internal.Cast.uncheckedNonnullCast;

/**
 * A service method factory that will try to use method handles if available, otherwise fallback on reflection.
 */
class DefaultServiceMethodFactory implements ServiceMethodFactory {
    private final ServiceMethodFactory delegate = getOptimalServiceMethodFactory();

    private ServiceMethodFactory getOptimalServiceMethodFactory() {
        try {
            return uncheckedNonnullCast(
                Class.forName("org.gradle.internal.service.MethodHandleBasedServiceMethodFactory").getConstructor().newInstance()
            );
        } catch (Exception e) {
            return new ReflectionBasedServiceMethodFactory();
        }
    }

    @Override
    public ServiceMethod toServiceMethod(Method method) {
        return delegate.toServiceMethod(method);
    }
}
