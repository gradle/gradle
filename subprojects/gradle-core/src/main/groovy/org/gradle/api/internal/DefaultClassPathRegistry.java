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

package org.gradle.api.internal;

import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class DefaultClassPathRegistry implements ClassPathRegistry {
    private final List<ClassPathProvider> providers = new ArrayList<ClassPathProvider>();

    public DefaultClassPathRegistry(ClassPathProvider... providers) {
        this.providers.addAll(Arrays.asList(providers));
        this.providers.add(new DefaultClassPathProvider());
    }

    public URL[] getClassPathUrls(String name) {
        return toURLArray(getClassPathFiles(name));
    }

    public Set<URL> getClassPath(String name) {
        return toUrlSet(getClassPathFiles(name));
    }

    public Set<File> getClassPathFiles(String name) {
        for (ClassPathProvider provider : providers) {
            Set<File> classpath = provider.findClassPath(name);
            if (classpath != null) {
                return classpath;
            }
        }
        throw new IllegalArgumentException(String.format("unknown classpath '%s' requested.", name));
    }

    private Set<URL> toUrlSet(Set<File> classPathFiles) {
        Set<URL> urls = new LinkedHashSet<URL>();
        for (File file : classPathFiles) {
            try {
                urls.add(file.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new UncheckedIOException(e);
            }
        }
        return urls;
    }

    private URL[] toURLArray(Collection<File> files) {
        List<URL> urls = new ArrayList<URL>(files.size());
        for (File file : files) {
            try {
                urls.add(file.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new UncheckedIOException(e);
            }
        }
        return urls.toArray(new URL[urls.size()]);
    }
}
