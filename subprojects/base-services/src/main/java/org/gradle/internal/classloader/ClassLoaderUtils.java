/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.JavaVersion;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.reflect.JavaMethod;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLConnection;

import static org.gradle.internal.reflect.JavaReflectionUtil.method;

public abstract class ClassLoaderUtils {
    private static final ClassDefiner CLASS_DEFINER;

    private static final ClassLoaderPackagesFetcher CLASS_LOADER_PACKAGES_FETCHER;

    static {
        CLASS_DEFINER = JavaVersion.current().isJava9Compatible()?  new LookupClassDefiner(): new ReflectionClassDefiner();
        CLASS_LOADER_PACKAGES_FETCHER = JavaVersion.current().isJava9Compatible()? new LookupPackagesFetcher(): new ReflectionPackagesFetcher();
    }

    /**
     * Returns the ClassLoader that contains the Java platform classes only. This is different to {@link ClassLoader#getSystemClassLoader()}, which includes the application classes in addition to the
     * platform classes.
     */
    public static ClassLoader getPlatformClassLoader() {
        return ClassLoader.getSystemClassLoader().getParent();
    }

    public static void tryClose(@Nullable ClassLoader classLoader) {
        CompositeStoppable.stoppable(classLoader).stop();
    }

    public static void disableUrlConnectionCaching() {
        // fix problems in updating jar files by disabling default caching of URL connections.
        // URLConnection default caching should be disabled since it causes jar file locking issues and JVM crashes in updating jar files.
        // Changes to jar files won't be noticed in all cases when caching is enabled.
        // sun.net.www.protocol.jar.JarURLConnection leaves the JarFile instance open if URLConnection caching is enabled.
        try {
            URL url = new URL("jar:file://valid_jar_url_syntax.jar!/");
            URLConnection urlConnection = url.openConnection();
            urlConnection.setDefaultUseCaches(false);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    static Package[] getPackages(ClassLoader classLoader) {
        return CLASS_LOADER_PACKAGES_FETCHER.getPackages(classLoader);
    }

    static Package getPackage(ClassLoader classLoader, String name) {
        return CLASS_LOADER_PACKAGES_FETCHER.getPackage(classLoader, name);
    }

    public static <T> Class<T> define(ClassLoader targetClassLoader, String className, byte[] clazzBytes) {
        return CLASS_DEFINER.defineClass(targetClassLoader, className, clazzBytes);
    }

    private interface ClassDefiner {
        <T> Class<T> defineClass(ClassLoader classLoader, String className, byte[] classBytes);
    }

    private interface ClassLoaderPackagesFetcher {
        Package[] getPackages(ClassLoader classLoader);

        Package getPackage(ClassLoader classLoader, String name);
    }

    private static class ReflectionClassDefiner implements ClassDefiner {
        private final JavaMethod<ClassLoader, Class> defineClassMethod;

        private ReflectionClassDefiner() {
            defineClassMethod = method(ClassLoader.class, Class.class, "defineClass", String.class, byte[].class, int.class, int.class);
        }

        @Override
        public <T> Class<T> defineClass(ClassLoader classLoader, String className, byte[] classBytes) {
            return Cast.uncheckedCast(defineClassMethod.invoke(classLoader, className, classBytes, 0, classBytes.length));
        }
    }

    private static class LookupClassDefiner implements ClassDefiner {
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
        @SuppressWarnings("unchecked")
        public <T> Class<T> defineClass(ClassLoader classLoader, String className, byte[] classBytes) {
            try {
                return (Class) defineClassMethodHandle.bindTo(classLoader).invokeWithArguments(className, classBytes, 0, classBytes.length);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class ReflectionPackagesFetcher implements ClassLoaderPackagesFetcher {
        private static final JavaMethod<ClassLoader, Package[]> GET_PACKAGES_METHOD = method(ClassLoader.class, Package[].class, "getPackages");
        private static final JavaMethod<ClassLoader, Package> GET_PACKAGE_METHOD = method(ClassLoader.class, Package.class, "getPackage", String.class);

        @Override
        public Package[] getPackages(ClassLoader classLoader) {
            return GET_PACKAGES_METHOD.invoke(classLoader);
        }

        @Override
        public Package getPackage(ClassLoader classLoader, String name) {
            return GET_PACKAGE_METHOD.invoke(classLoader, name);
        }
    }

    private static class LookupPackagesFetcher implements ClassLoaderPackagesFetcher {
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
                return (Package[]) getPackagesMethodHandle.bindTo(classLoader).invokeWithArguments();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Package getPackage(ClassLoader classLoader, String name) {
            try {
                return (Package) getDefinedPackageMethodHandle.bindTo(classLoader).invokeWithArguments(name);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }
}
