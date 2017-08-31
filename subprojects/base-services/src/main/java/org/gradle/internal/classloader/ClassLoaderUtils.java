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

import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;
import sun.misc.Unsafe;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public abstract class ClassLoaderUtils {

    private static final Unsafe UNSAFE;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static final JavaMethod<ClassLoader, Package[]> GET_PACKAGES_METHOD;
    private static final JavaMethod<ClassLoader, Package> GET_PACKAGE_METHOD;

    static {
        GET_PACKAGES_METHOD = getMethodWithFallback(Package[].class, new Class[0], "getDefinedPackages", "getPackages");
        GET_PACKAGE_METHOD = getMethodWithFallback(Package.class, new Class[] {String.class}, "getDefinedPackage", "getPackage");
    }

    private static <T> JavaMethod<ClassLoader, T> getMethodWithFallback(Class<T> clazz, Class<?>[] params, String firstChoice, String fallback) {
        JavaMethod<ClassLoader, T> method;
        try {
            method = JavaReflectionUtil.method(ClassLoader.class, clazz, firstChoice, params);
        } catch (Throwable e) {
            // We must not be on Java 9 where the getDefinedPackages() method exists. Fall back to getPackages()
            method = JavaReflectionUtil.method(ClassLoader.class, clazz, fallback, params);
        }
        return method;
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
        } catch (MalformedURLException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public static JavaMethod<ClassLoader, Package[]> getPackagesMethod() {
        return GET_PACKAGES_METHOD;
    }

    public static JavaMethod<ClassLoader, Package> getPackageMethod() {
        return GET_PACKAGE_METHOD;
    }

    public static <T> Class<T> define(ClassLoader targetClassLoader, String className, byte[] clazzBytes) {
        return Cast.uncheckedCast(UNSAFE.defineClass(className, clazzBytes, 0, clazzBytes.length, targetClassLoader, null));
    }
}
