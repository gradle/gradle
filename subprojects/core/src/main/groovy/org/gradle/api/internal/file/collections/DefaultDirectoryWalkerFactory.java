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

package org.gradle.api.internal.file.collections;

import org.gradle.api.JavaVersion;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.resource.CharsetUtil;

import java.nio.charset.Charset;

public class DefaultDirectoryWalkerFactory implements Factory<DirectoryWalker> {
    private final JavaVersion javaVersion;
    private final ClassLoader classLoader;
    private DirectoryWalker instance;

    DefaultDirectoryWalkerFactory(JavaVersion javaVersion, ClassLoader classLoader) {
        this.javaVersion = javaVersion;
        this.classLoader = classLoader;
        reset();
    }

    DefaultDirectoryWalkerFactory() {
        this(JavaVersion.current(), DefaultDirectoryWalkerFactory.class.getClassLoader());
    }

    public DirectoryWalker create() {
        return instance;
    }

    private void reset() {
        this.instance = createInstance();
    }

    private DirectoryWalker createInstance() {
        if (javaVersion.isJava7Compatible() && isUnicodeSupported()) {
            try {
                Class clazz = classLoader.loadClass("org.gradle.api.internal.file.collections.jdk7.Jdk7DirectoryWalker");
                return Cast.uncheckedCast(DirectInstantiator.instantiate(clazz));
            } catch (ClassNotFoundException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        } else {
            return new DefaultDirectoryWalker();
        }
    }

    private boolean isUnicodeSupported() {
        return Charset.defaultCharset().contains(CharsetUtil.UTF_8);
    }
}
