/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.classloader;

import org.gradle.internal.Cast;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

class LookupClassDefiner implements ClassDefiner {
    private final MethodHandle defineClassMethodHandle;

    LookupClassDefiner() {
        try {
            MethodHandles.Lookup baseLookup = MethodHandles.lookup();
            MethodType defineClassMethodType = MethodType.methodType(Class.class, new Class[]{String.class, byte[].class, int.class, int.class});
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ClassLoader.class, baseLookup);
            defineClassMethodHandle = lookup.findVirtual(ClassLoader.class, "defineClass", defineClassMethodType);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> Class<T> defineClass(ClassLoader classLoader, String className, byte[] classBytes) {
        try {
            return Cast.uncheckedCast(defineClassMethodHandle.bindTo(classLoader).invokeWithArguments(className, classBytes, 0, classBytes.length));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}

