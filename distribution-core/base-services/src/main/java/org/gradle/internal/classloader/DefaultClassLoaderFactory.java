/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.service.CachingServiceLocator;
import org.gradle.internal.service.DefaultServiceLocator;
import org.gradle.internal.service.ServiceLocator;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.util.Collections;

import static java.lang.ClassLoader.getSystemClassLoader;
import static org.gradle.internal.classloader.ClasspathUtil.getClasspathForClass;
import static org.gradle.internal.classloader.ClasspathUtil.getClasspathForResource;

public class DefaultClassLoaderFactory implements ClassLoaderFactory {
    // This uses the system classloader and will not release any loaded classes for the life of the daemon process.
    // Do not use this to load any classes which are part of the build; it will not release them when the build is complete.
    private final CachingServiceLocator systemClassLoaderServiceLocator = CachingServiceLocator.of(new DefaultServiceLocator(getSystemClassLoader()));

    @Override
    public ClassLoader getIsolatedSystemClassLoader() {
        return getSystemClassLoader().getParent();
    }

    @Override
    public ClassLoader createIsolatedClassLoader(String name, ClassPath classPath) {
        // This piece of ugliness copies the JAXP (ie XML API) provider, if any, from the system ClassLoader. Here's why:
        //
        // 1. When looking for a provider, JAXP looks for a service resource in the context ClassLoader, which is our isolated ClassLoader. If our classpath above does not contain a
        //    provider, this returns null. If it does contain a provider, JAXP extracts the classname from the service resource.
        // 2. If not found, JAXP looks for a service resource in the system ClassLoader. This happens to include all the application classes specified on the classpath. If the application
        //    classpath does not contain a provider, this returns null. If it does contain a provider, JAXP extracts the implementation classname from the service resource.
        // 3. If not found, JAXP uses a default classname
        // 4. JAXP attempts to load the provider using the context ClassLoader. which is our isolated ClassLoader. This is fine if the classname came from step 1 or 3. It blows up if the
        //    classname came from step 2.
        //
        // So, as a workaround, locate and make visible XML parser classes from the system classloader in our isolated ClassLoader.
        //
        // Note that in practise, this is only triggered when running in our tests

        if (needJaxpImpl()) {
            classPath = addToClassPath(classPath, getClasspathForResource(getSystemClassLoader(), "META-INF/services/javax.xml.parsers.SAXParserFactory"));
            classPath = addToClassPath(classPath, getClasspathForClass("org.w3c.dom.ElementTraversal"));
        }

        return doCreateClassLoader(name, getIsolatedSystemClassLoader(), classPath);
    }

    private ClassPath addToClassPath(ClassPath classPath, File file) {
        if (file != null) {
            return classPath.plus(Collections.singletonList(file));
        }
        return classPath;
    }

    @Override
    public ClassLoader createFilteringClassLoader(ClassLoader parent, FilteringClassLoader.Spec spec) {
        // See the comment for {@link #createIsolatedClassLoader} above
        FilteringClassLoader.Spec classLoaderSpec = new FilteringClassLoader.Spec(spec);
        if (needJaxpImpl()) {
            makeServiceVisible(systemClassLoaderServiceLocator, classLoaderSpec, SAXParserFactory.class);
            makeServiceVisible(systemClassLoaderServiceLocator, classLoaderSpec, DocumentBuilderFactory.class);
            makeServiceVisible(systemClassLoaderServiceLocator, classLoaderSpec, DatatypeFactory.class);
        }
        return doCreateFilteringClassLoader(parent, classLoaderSpec);
    }

    protected ClassLoader doCreateClassLoader(String name, ClassLoader parent, ClassPath classPath) {
        return new VisitableURLClassLoader(name, parent, classPath);
    }

    protected ClassLoader doCreateFilteringClassLoader(ClassLoader parent, FilteringClassLoader.Spec spec) {
        return new FilteringClassLoader(parent, spec);
    }

    private static void makeServiceVisible(ServiceLocator locator, FilteringClassLoader.Spec classLoaderSpec, Class<?> serviceType) {
        classLoaderSpec.allowClass(locator.getFactory(serviceType).getImplementationClass());
        classLoaderSpec.allowResource("META-INF/services/" + serviceType.getName());
    }

    private static boolean needJaxpImpl() {
        return ClassLoader.getSystemResource("META-INF/services/javax.xml.parsers.SAXParserFactory") != null;
    }

}
