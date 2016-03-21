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

import com.google.common.base.Charsets;
import org.gradle.api.JavaVersion;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.nativeintegration.services.FileSystems;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.reflect.DirectInstantiator;

import java.nio.charset.Charset;

public class DefaultDirectoryWalkerFactory implements Factory<DirectoryWalker> {
    private final ClassLoader classLoader;
    private final JavaVersion javaVersion;
    private DirectoryWalker instance;

    DefaultDirectoryWalkerFactory(ClassLoader classLoader, JavaVersion javaVersion) {
        this.javaVersion = javaVersion;
        this.classLoader = classLoader;
        reset();
    }

    DefaultDirectoryWalkerFactory() {
        this(DefaultDirectoryWalkerFactory.class.getClassLoader(), JavaVersion.current());
    }

    public DirectoryWalker create() {
        return instance;
    }

    private void reset() {
        this.instance = createInstance();
    }

    private DirectoryWalker createInstance() {
        FileSystem fileSystem = FileSystems.getDefault();
        if (javaVersion.isJava8Compatible() || (javaVersion.isJava7Compatible() && defaultEncodingContainsPlatformEncoding())) {
            try {
                Class clazz = classLoader.loadClass("org.gradle.api.internal.file.collections.jdk7.Jdk7DirectoryWalker");
                return Cast.uncheckedCast(DirectInstantiator.instantiate(clazz, fileSystem));
            } catch (ClassNotFoundException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        } else {
            return new DefaultDirectoryWalker(fileSystem);
        }
    }

    private boolean defaultEncodingContainsPlatformEncoding() {
        // sun.jnu.encoding is the platform encoding used to decode/encode file paths, command line arguments, etc.
        // it's derived from LANG/LC_ALL/LC_CTYPE on Unixes and should not be set by the user
        String platformEncoding = System.getProperty("sun.jnu.encoding");
        Charset platformCharset = platformEncoding != null && Charset.isSupported(platformEncoding) ? Charset.forName(platformEncoding) : null;
        // fallback to require UTF-8 when platformCharset cannot be resolved
        Charset requiredCharset = platformCharset != null ? platformCharset : Charsets.UTF_8;
        return Charset.defaultCharset().contains(requiredCharset);
    }
}
