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

public class LookupPackagesFetcher implements ClassLoaderPackagesFetcher {
    private final MethodHandle getPackagesMethodHandle;
    private final MethodHandle getDefinedPackageMethodHandle;

    LookupPackagesFetcher() {
        try {
            MethodHandles.Lookup baseLookup = MethodHandles.lookup();
            MethodType getPackagesMethodType = MethodType.methodType(Package[].class, new Class[]{});
            MethodType getDefinedPackageMethodType = MethodType.methodType(Package.class, new Class[]{String.class});
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ClassLoader.class, baseLookup);
            getPackagesMethodHandle = lookup.findVirtual(ClassLoader.class, "getPackages", getPackagesMethodType);
            getDefinedPackageMethodHandle = lookup.findVirtual(ClassLoader.class, "getDefinedPackage", getDefinedPackageMethodType);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Package[] getPackages(ClassLoader classLoader) {
        try {
            return Cast.uncheckedCast(getPackagesMethodHandle.bindTo(classLoader).invokeWithArguments());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Package getPackage(ClassLoader classLoader, String name) {
        try {
            return Cast.uncheckedCast(getDefinedPackageMethodHandle.bindTo(classLoader).invokeWithArguments(name));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
