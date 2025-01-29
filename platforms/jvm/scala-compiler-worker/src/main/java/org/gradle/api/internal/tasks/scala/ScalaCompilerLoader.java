/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.scala;

/*
 * Dotty (https://dotty.epfl.ch) Copyright 2012-2020 EPFL Copyright 2012-2020 Lightbend, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.net.URL;
import java.net.URLClassLoader;

// based on
// https://github.com/lampepfl/dotty/blob/96401b68a8bce4c125859806b0c24a1ffe3cbc1e/sbt-bridge/src/xsbt/CompilerClassLoader.java
public class ScalaCompilerLoader extends URLClassLoader {
    private final ClassLoader sbtLoader;

    public ScalaCompilerLoader(URL[] urls, ClassLoader sbtLoader) {
        super(urls, null);
        this.sbtLoader = sbtLoader;
    }

    @Override
    public Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
        if (className.startsWith("xsbti.")) {
            // We can't use the loadClass overload with two arguments because it's
            // protected, but we can do the same by hand (the classloader instance
            // from which we call resolveClass does not matter).
            Class<?> c = sbtLoader.loadClass(className);
            if (resolve) {
                resolveClass(c);
            }
            return c;
        } else {
            return super.loadClass(className, resolve);
        }
    }
}
