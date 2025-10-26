/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.TransformedClassPath;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.gradle.internal.Cast.uncheckedNonnullCast;

public class VisitableURLClassLoader extends URLClassLoader implements ClassLoaderHierarchy {
    static {
        try {
            registerAsParallelCapable();
        } catch (NoSuchMethodError ignore) {
            // Not supported on Java 6
        }
    }

    private final Map<Object, Object> userData = new HashMap<>();
    private volatile boolean closed = false;

    /**
     * This method can be used to store user data that should live among with this classloader
     *
     * @param consumerId the consumer
     * @param onMiss called to create the initial data, when not found
     * @param <T> the type of data
     * @return user data
     */
    public synchronized <T> T getUserData(Object consumerId, Factory<T> onMiss) {
        if (closed) {
            throw new IllegalStateException("ClassLoader is closed");
        }
        if (userData.containsKey(consumerId)) {
            return uncheckedNonnullCast(userData.get(consumerId));
        }
        T value = onMiss.create();
        userData.put(consumerId, value);
        return value;
    }

    // TODO:lptr When we drop Java 8 support we can switch to using ClassLoader.getName() instead of storing our own
    private final String name;

    public VisitableURLClassLoader(String name, ClassLoader parent, Collection<URL> urls) {
        this(name, urls.toArray(new URL[0]), parent);
    }

    protected VisitableURLClassLoader(String name, ClassLoader parent, ClassPath classPath) {
        this(name, classPath.getAsURLArray(), parent);
        if (classPath instanceof TransformedClassPath && !(this instanceof InstrumentingClassLoader)) {
            throw new IllegalArgumentException("Cannot build a non-instrumenting class loader " + name + " out of transformed class path");
        }
    }

    private VisitableURLClassLoader(String name, URL[] classpath, ClassLoader parent) {
        super(classpath, parent);
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + name + ")";
    }

    @Override
    public void visit(ClassLoaderVisitor visitor) {
        URL[] urls = getURLs();
        visitor.visitSpec(new Spec(name, Arrays.asList(urls)));
        visitor.visitClassPath(urls);
        visitor.visitParent(getParent());
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        // Clear user data to prevent memory leaks
        synchronized (this) {
            userData.clear();
        }
        // Close resources in proper order
        super.close();
    }

    /**
     * Additional cleanup method that can be called explicitly
     * when the classloader is no longer needed but not yet garbage collected.
     */
    public void cleanup() {
        if (!closed) {
            try {
                close();
            } catch (IOException ignore) {
                // Not supported on Java 6
            }
        }
    }

    public static class Spec extends ClassLoaderSpec {
        final String name;
        final List<URL> classpath;

        public String getName() {
            return name;
        }

        public Spec(String name, List<URL> classpath) {
            this.name = name;
            this.classpath = classpath;
        }

        public List<URL> getClasspath() {
            return classpath;
        }

        @Override
        public String toString() {
            return "{url-class-loader name:" + name + ", classpath:" + classpath + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            return classpath.equals(((Spec) obj).classpath);
        }

        @Override
        public int hashCode() {
            return classpath.hashCode();
        }
    }

    public static VisitableURLClassLoader fromClassPath(String name, ClassLoader parent, ClassPath classPath) {
        if (classPath instanceof TransformedClassPath) {
            return new InstrumentingVisitableURLClassLoader(name, parent, (TransformedClassPath) classPath);
        }
        return new VisitableURLClassLoader(name, parent, classPath);
    }

    private static class InstrumentingVisitableURLClassLoader extends VisitableURLClassLoader implements InstrumentingClassLoader {
        static {
            try {
                // Not supported on Java 6, hence the try-catch
                registerAsParallelCapable();
            } catch (NoSuchMethodError ignore) {
                // ignore in Java 6
            }
        }

        private final TransformReplacer replacer;
        private final TransformErrorHandler errorHandler;

        public InstrumentingVisitableURLClassLoader(String name, ClassLoader parent, TransformedClassPath classPath) {
            super(name, parent, classPath);
            replacer = new TransformReplacer(classPath);
            errorHandler = new TransformErrorHandler(name);
        }

        @Override
        public byte @Nullable [] instrumentClass(@Nullable String className, @Nullable ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            return replacer.getInstrumentedClass(className, protectionDomain);
        }

        @Override
        public void transformFailed(@Nullable String className, Throwable cause) {
            errorHandler.classLoadingError(className, cause);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            errorHandler.enterClassLoadingScope(name);
            try {
                return super.findClass(name);
            } catch (Throwable e) {
                throw errorHandler.exitClassLoadingScopeWithException(e);
            } finally {
                errorHandler.exitClassLoadingScope();
            }
        }

        @Override
        public void close() throws IOException {
            if (isClosed()) {
                return;
            }

            // Close resources in proper order
            closeQuietly(replacer);
            if (errorHandler instanceof AutoCloseable) {
                closeQuietly((Closeable) errorHandler);
            }

            super.close();
        }

        private boolean isClosed() {
            try {
                return (Boolean) getClass().getSuperclass().getDeclaredField("closed").get(this);
            } catch (Exception e) {
                return false;
            }
        }
    }
}
