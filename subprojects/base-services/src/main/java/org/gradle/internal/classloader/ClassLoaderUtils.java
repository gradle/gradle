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
import java.net.URL;
import java.net.URLConnection;

import static org.gradle.internal.reflect.JavaReflectionUtil.method;

public abstract class ClassLoaderUtils {
    private static final ClassDefiner CLASS_DEFINER;

    private static final ClassLoaderPackagesFetcher CLASS_LOADER_PACKAGES_FETCHER;

    static {
        CLASS_DEFINER = JavaVersion.current().isJava9Compatible() ? loadLookupClassDefiner() : new ReflectionClassDefiner();
        CLASS_LOADER_PACKAGES_FETCHER = JavaVersion.current().isJava9Compatible() ? loadLookupPackagesFetcher() : new ReflectionPackagesFetcher();
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

    static ClassLoaderPackagesFetcher getClassLoaderPackagesFetcher() {
        return CLASS_LOADER_PACKAGES_FETCHER;
    }

    public static <T> Class<T> define(ClassLoader targetClassLoader, String className, byte[] clazzBytes) {
        return CLASS_DEFINER.defineClass(targetClassLoader, className, clazzBytes);
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

    private static ClassDefiner loadLookupClassDefiner() {
        try {
            return (ClassDefiner) Class.forName("org.gradle.internal.classloader.LookupClassDefiner").newInstance();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static ClassLoaderPackagesFetcher loadLookupPackagesFetcher() {
        try {
            return (ClassLoaderPackagesFetcher) Class.forName("org.gradle.internal.classloader.LookupPackagesFetcher").newInstance();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static class ReflectionPackagesFetcher implements ClassLoaderPackagesFetcher {
        private final JavaMethod<ClassLoader, Package[]> GET_PACKAGES_METHOD = method(ClassLoader.class, Package[].class, "getPackages");
        private final JavaMethod<ClassLoader, Package> GET_PACKAGE_METHOD = method(ClassLoader.class, Package.class, "getPackage", String.class);

        @Override
        public Package[] getPackages(ClassLoader classLoader) {
            return GET_PACKAGES_METHOD.invoke(classLoader);
        }

        @Override
        public Package getPackage(ClassLoader classLoader, String name) {
            return GET_PACKAGE_METHOD.invoke(classLoader, name);
        }
    }
}
