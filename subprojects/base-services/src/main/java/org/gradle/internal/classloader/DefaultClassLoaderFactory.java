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
package org.gradle.internal.classloader;

import org.gradle.internal.Transformers;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.service.ServiceLocator;
import org.gradle.util.CollectionUtils;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.List;

public class DefaultClassLoaderFactory implements ClassLoaderFactory {
    @Override
    public ClassLoader getIsolatedSystemClassLoader() {
        return getSystemClassLoader().getParent();
    }

    public ClassLoader createIsolatedClassLoader(Iterable<URI> uris) {
        return doCreateIsolatedClassLoader(CollectionUtils.collect(uris, Transformers.toURL()));
    }

    public ClassLoader createIsolatedClassLoader(ClassPath classPath) {
        return doCreateIsolatedClassLoader(classPath.getAsURLs());
    }

    private ClassLoader doCreateIsolatedClassLoader(Collection<URL> classpath) {
        return new MutableURLClassLoader(getIsolatedSystemClassLoader(), classpath);
    }

    public FilteringClassLoader createFilteringClassLoader(ClassLoader parent) {
        // This piece of ugliness makes XML parsing classes visible in some cases. Here's why:
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
        // Note that in practise, this is triggered when running in our tests and running in embedded mode with the tooling API.
        FilteringClassLoader classLoader = new FilteringClassLoader(parent);
        if (needJaxpImpl()) {
            ServiceLocator locator = new ServiceLocator(getSystemClassLoader());
            makeServiceVisible(locator, classLoader, SAXParserFactory.class);
            makeServiceVisible(locator, classLoader, DocumentBuilderFactory.class);
            makeServiceVisible(locator, classLoader, DatatypeFactory.class);
        }
        return classLoader;
    }

    public ClassLoader createClassLoader(ClassLoaderSpec spec, List<? extends ClassLoader> parents) {
        if (spec instanceof MultiParentClassLoader.Spec) {
            return new MultiParentClassLoader(parents);
        }
        if (parents.size() != 1) {
            throw new IllegalArgumentException("Expected a single parent.");
        }
        ClassLoader parent = parents.get(0);
        if (spec instanceof MutableURLClassLoader.Spec) {
            MutableURLClassLoader.Spec clSpec = (MutableURLClassLoader.Spec) spec;
            return new MutableURLClassLoader(parent, clSpec);
        }
        if (spec instanceof CachingClassLoader.Spec) {
            return new CachingClassLoader(parent);
        }
        if (spec instanceof FilteringClassLoader.Spec) {
            FilteringClassLoader.Spec clSpec = (FilteringClassLoader.Spec) spec;
            return new FilteringClassLoader(parent, clSpec);
        }
        throw new IllegalArgumentException(String.format("Don't know how to create a ClassLoader from spec %s", spec));
    }

    @Override
    public FilteringClassLoader createSystemFilteringClassLoader() {
        return createFilteringClassLoader(getSystemClassLoader());
    }

    private void makeServiceVisible(ServiceLocator locator, FilteringClassLoader classLoader, Class<?> serviceType) {
        classLoader.allowClass(locator.getFactory(serviceType).getImplementationClass());
        classLoader.allowResource("META-INF/services/" + serviceType.getName());
    }

    private boolean needJaxpImpl() {
        return ClassLoader.getSystemResource("META-INF/services/javax.xml.parsers.SAXParserFactory") != null;
    }

    private ClassLoader getSystemClassLoader() {
        return ClassLoader.getSystemClassLoader();
    }
}
