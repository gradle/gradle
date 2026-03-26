/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.filter;

import org.gradle.internal.classloader.FilteringClassLoader;

public class AnnotationProcessorFilter {
    public static FilteringClassLoader getFilteredClassLoader(ClassLoader parent) {
        return new FilteringClassLoader(parent, getExtraAllowedPackages());
    }

    /**
     * Many popular annotation processors like lombok need access to compiler internals
     * to do their magic, e.g. to inspect or even change method bodies. This is not valid
     * according to the annotation processing spec, but forbidding it would upset a lot of
     * our users.
     */
    private static FilteringClassLoader.Spec getExtraAllowedPackages() {
        FilteringClassLoader.Spec spec = new FilteringClassLoader.Spec();
        spec.allowPackage("com.sun.tools.javac");
        spec.allowPackage("com.sun.source");
        return spec;
    }
}
