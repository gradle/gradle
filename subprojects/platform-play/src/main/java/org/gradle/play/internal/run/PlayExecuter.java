/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.run;

import org.gradle.scala.internal.reflect.ScalaMethod;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

public class PlayExecuter {
    public JarFile getDocJar(Iterable<File> docsClasspath) throws IOException {
        File docJarFile = null;
        for (File file: docsClasspath) {
            if (file.getName().startsWith("play-docs")) {
                docJarFile = file;
                break;
            }
        }
        return new JarFile(docJarFile);
    }

    public void run(VersionedPlayRunSpec spec) {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            ClassLoader docsClassLoader = getClass().getClassLoader();

            Object buildDocHandler = spec.getBuildDocHandler(docsClassLoader, getDocJar(spec.getClasspath()));
            ScalaMethod runMethod = spec.getNettyServerDevHttpMethod(classLoader, docsClassLoader);
            Object buildLink = spec.getBuildLink(classLoader);
            runMethod.invoke(buildLink, buildDocHandler, spec.getHttpPort());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
