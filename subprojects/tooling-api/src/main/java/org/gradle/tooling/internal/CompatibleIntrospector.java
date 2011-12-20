/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.internal;

import org.gradle.tooling.model.IncompatibleVersionException;

import java.lang.reflect.Method;

/**
 * Very simple tool for finding out if instance of a protocol class supports given method.
 * <p>
 * It is useful when the target instance of the protocol object was created without
 * wrapping it in a decorating proxy ({@link org.gradle.tooling.internal.consumer.ProtocolToModelAdapter})
 * that normally throws the {@link IncompatibleVersionException}.
 * This also applies to older versions of Gradle, e.g. the protocol object was
 * created without proxy in one of the older versions of Gradle, even though currently it is wrapped in a decorating proxy.
 *
 * by Szczepan Faber, created at: 12/9/11
 */
public class CompatibleIntrospector {

    private final Object target;

    public CompatibleIntrospector(Object target) {
        this.target = target;
    }

    private Method getMethod(String methodName) {
        try {
            return target.getClass().getDeclaredMethod(methodName, new Class[0]);
        } catch (NoSuchMethodException e) {
            throw new IncompatibleVersionException("The method: " + methodName + " is not supported on instance: " + target + ".\n", e);
        }
    }

    public <T> T getSafely(T defaultValue, String methodName) {
        try {
            Method method = getMethod(methodName);
            method.setAccessible(true);
            return (T) method.invoke(target);
        } catch (IncompatibleVersionException e) {
            return defaultValue;
        } catch (Exception e) {
            throw new RuntimeException("Unable to get value reflectively", e);
        }
    }
}