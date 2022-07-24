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
import org.gradle.internal.Factory;
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

public abstract class ClassLoaderUtils {
    private static final ClassDefiner CLASS_DEFINER;

    private static final ClassLoaderPackagesFetcher CLASS_LOADER_PACKAGES_FETCHER;

    static {
        CLASS_DEFINER = JavaVersion.current().isJava9Compatible() ? new LookupClassDefiner() : new ReflectionClassDefiner();
        CLASS_LOADER_PACKAGES_FETCHER = JavaVersion.current().isJava9Compatible() ? new Java9PackagesFetcher() : new ReflectionPackagesFetcher();
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

    // Used by the Gradle Play Framework Plugin. See:
    // https://github.com/gradle/playframework/blob/master/src/main/java/org/gradle/playframework/tools/internal/run/PlayWorkerServer.java#L72
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

    @Nullable
    static Package getPackage(ClassLoader classLoader, String name) {
        return CLASS_LOADER_PACKAGES_FETCHER.getPackage(classLoader, name);
    }

    public static <T> Class<T> define(ClassLoader targetClassLoader, String className, byte[] clazzBytes) {
        return CLASS_DEFINER.defineClass(targetClassLoader, className, clazzBytes);
    }

    public static <T> Class<T> defineDecorator(Class<?> decoratedClass, ClassLoader targetClassLoader, String className, byte[] clazzBytes) {
        return CLASS_DEFINER.defineDecoratorClass(decoratedClass, targetClassLoader, className, clazzBytes);
    }

    public static Class<?> classFromContextLoader(String className) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Nullable
    public static <T> T executeInClassloader(ClassLoader classLoader, Factory<T> factory) {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            return factory.create();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    /**
     * Define a class into a class loader.
     *
     * On Java 8, the implementation is simply invoking {@link ClassLoader#defineClass} reflectively.
     *
     * Since Java 9, reflection is severely restrained, and a new API {@link MethodHandles.Lookup#defineClass} is introduced.
     * However, this API can only "defines a class to the same class loader and in the same runtime package and protection domain as this lookup's lookup class",
     * which means, we can only use this API safely in the decorating scenario where the decorated class acts as the lookup object.
     *
     * Otherwise, we have to use {@link MethodHandle} to invoke {@link ClassLoader#defineClass}. Fortunately, this is the rare case.
     */
    private interface ClassDefiner {
        <T> Class<T> defineClass(ClassLoader classLoader, String className, byte[] classBytes);

        <T> Class<T> defineDecoratorClass(Class<?> decoratedClass, ClassLoader classLoader, String className, byte[] classBytes);
    }

    private interface ClassLoaderPackagesFetcher {
        Package[] getPackages(ClassLoader classLoader);

        @Nullable
        Package getPackage(ClassLoader classLoader, String name);
    }

    /**
     * This class makes it a bit easier to use {@link MethodHandles.Lookup}.
     * In order to access a method, a lookup object which is accessible to this method must be provided.
     * Usually, this class and the target Gradle-managed class loader exist in the same module, so everything works.
     * Otherwise, an {@link IllegalAccessException} will be thrown, and {@link ClassLoader} class will be used as the lookup object.
     */
    private static class AbstractClassLoaderLookuper {
        protected MethodHandles.Lookup baseLookup;

        protected AbstractClassLoaderLookuper() {
            try {
                baseLookup = MethodHandles.lookup();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("unchecked")
        protected <T> T invoke(ClassLoader classLoader, String methodName, MethodType methodType, Object... arguments) {
            try {
                MethodHandles.Lookup lookup = getLookupForClassLoader(classLoader);
                MethodHandle methodHandle = lookup.findVirtual(ClassLoader.class, methodName, methodType);
                return (T) methodHandle.bindTo(classLoader).invokeWithArguments(arguments);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        private MethodHandles.Lookup getLookupForClassLoader(ClassLoader classLoader) throws IllegalAccessException {
            try {
                return MethodHandles.privateLookupIn(classLoader.getClass(), baseLookup);
            } catch (IllegalAccessException e) {
                // Fallback to ClassLoader's lookup
                return MethodHandles.privateLookupIn(ClassLoader.class, baseLookup);
            }
        }
    }

    private static class ReflectionClassDefiner implements ClassDefiner {
        @SuppressWarnings("rawtypes")
        private final JavaMethod<ClassLoader, Class> defineClassMethod;

        private ReflectionClassDefiner() {
            defineClassMethod = JavaMethod.of(ClassLoader.class, Class.class, "defineClass", String.class, byte[].class, int.class, int.class);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Class<T> defineClass(ClassLoader classLoader, String className, byte[] classBytes) {
            return (Class<T>) defineClassMethod.invoke(classLoader, className, classBytes, 0, classBytes.length);
        }

        @Override
        public <T> Class<T> defineDecoratorClass(Class<?> decoratedClass, ClassLoader classLoader, String className, byte[] classBytes) {
            return defineClass(classLoader, className, classBytes);
        }
    }

    private static class LookupClassDefiner extends AbstractClassLoaderLookuper implements ClassDefiner {
        private MethodType defineClassMethodType = MethodType.methodType(Class.class, new Class<?>[]{String.class, byte[].class, int.class, int.class});

        @Override
        @SuppressWarnings("unchecked")
        public <T> Class<T> defineDecoratorClass(Class<?> decoratedClass, ClassLoader classLoader, String className, byte[] classBytes) {
            try {
                // Lookup.defineClass can only define a class into same classloader as the lookup object.
                // We have to use the fallback defineClass() if they're not same, which is the case of ManagedProxyClassGenerator
                if (decoratedClass.getClassLoader() == classLoader) {
                    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(decoratedClass, baseLookup);
                    return (Class) lookup.defineClass(classBytes);
                } else {
                    return defineClass(classLoader, className, classBytes);
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public <T> Class<T> defineClass(ClassLoader classLoader, String className, byte[] classBytes) {
            return invoke(classLoader, "defineClass", defineClassMethodType, className, classBytes, 0, classBytes.length);
        }
    }

    private static class ReflectionPackagesFetcher implements ClassLoaderPackagesFetcher {
        private static final JavaMethod<ClassLoader, Package[]> GET_PACKAGES_METHOD = JavaMethod.of(ClassLoader.class, Package[].class, "getPackages");
        private static final JavaMethod<ClassLoader, Package> GET_PACKAGE_METHOD = JavaMethod.of(ClassLoader.class, Package.class, "getPackage", String.class);

        @Override
        public Package[] getPackages(ClassLoader classLoader) {
            return GET_PACKAGES_METHOD.invoke(classLoader);
        }

        @Nullable
        @Override
        public Package getPackage(ClassLoader classLoader, String name) {
            return GET_PACKAGE_METHOD.invoke(classLoader, name);
        }
    }

    private static class Java9PackagesFetcher implements ClassLoaderPackagesFetcher {

        @Override
        public Package[] getPackages(ClassLoader classLoader) {
            return new ClassLoader(classLoader) {
                @Override
                protected Package[] getPackages() {
                    return super.getPackages();
                }
            }.getPackages();
        }

        @Nullable
        @Override
        public Package getPackage(ClassLoader classLoader, String name) {
            return new ClassLoader(classLoader) {
                // TODO: This is deprecated for a reason. We should consider migrating if possible.
                @Override
                @SuppressWarnings("deprecation")
                protected Package getPackage(String name) {
                    return super.getPackage(name);
                }
            }.getPackage(name);
        }
    }
}
