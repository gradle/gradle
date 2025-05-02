/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.internal.Cast;
import org.gradle.internal.lazy.Lazy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;

public class MethodHandleUtils {
    public static MethodHandle findStaticOrThrowError(Class<?> refc, String name, MethodType methodType) {
        try {
            return MethodHandles.lookup().findStatic(refc, name, methodType);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static <T> T invokeKotlinStaticDefault(Lazy<MethodHandle> handle, int mask, Object... args) throws Throwable {
        Object[] argsWithDefaultMaskAndFlag = Arrays.copyOf(args, args.length + 2);
        argsWithDefaultMaskAndFlag[args.length] = mask;
        argsWithDefaultMaskAndFlag[args.length + 1] = null; // it's already there, but let's be clear
        return Cast.uncheckedCast(handle.get().invokeWithArguments(argsWithDefaultMaskAndFlag));
    }

    public static Lazy<MethodHandle> lazyKotlinStaticDefaultHandle(
        Class<?> owner, String name, Class<?> returnType, Class<?>... parameterTypes
    ) {
        return Lazy.locking().of(() -> findStaticOrThrowError(owner, name + "$default", kotlinDefaultMethodType(returnType, parameterTypes)));
    }

    private static MethodType kotlinDefaultMethodType(Class<?> returnType, Class<?>... parameterTypes) {
        Class<?>[] defaultParameterTypes = Arrays.copyOf(parameterTypes, parameterTypes.length + 2);
        defaultParameterTypes[parameterTypes.length] = int.class; // default value mask
        defaultParameterTypes[parameterTypes.length + 1] = Object.class; // default signature marker
        return MethodType.methodType(returnType, defaultParameterTypes);
    }
}
