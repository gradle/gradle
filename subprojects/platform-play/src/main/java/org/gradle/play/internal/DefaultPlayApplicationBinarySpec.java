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

package org.gradle.play.internal;

import com.google.common.collect.Sets;
import org.gradle.api.internal.AbstractBuildableModelElement;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.play.JvmClasses;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;
import org.gradle.play.platform.PlayPlatform;

import java.io.File;
import java.util.Set;

public class DefaultPlayApplicationBinarySpec extends BaseBinarySpec implements PlayApplicationBinarySpecInternal {
    private final JvmClasses classesDir = new DefaultJvmClasses();
    private LanguageSourceSet generatedScala;
    private LanguageSourceSet testScala;
    private PlayPlatform platform;
    private PlayToolChainInternal toolChain;
    private File jarFile;
    private JvmClasses testClasses = new DefaultJvmClasses();

    public PlayPlatform getTargetPlatform() {
        return platform;
    }

    public PlayToolChainInternal getToolChain() {
        return toolChain;
    }

    public File getJarFile() {
        return jarFile;
    }

    public void setTargetPlatform(PlayPlatform platform) {
        this.platform = platform;
    }

    public void setToolChain(PlayToolChainInternal toolChain) {
        this.toolChain = toolChain;
    }

    public void setJarFile(File file) {
        this.jarFile = file;
    }

    public JvmClasses getClasses() {
        return classesDir;
    }

    public JvmClasses getTestClasses() {
        return testClasses;
    }

    public LanguageSourceSet getGeneratedScala() {
        return generatedScala;
    }

    public LanguageSourceSet getTestScala() {
        return testScala;
    }

    public void setGeneratedScala(LanguageSourceSet scalaSources) {
        this.generatedScala = scalaSources;
    }

    public void setTestScala(LanguageSourceSet scalaSources) {
        this.testScala = scalaSources;
    }

    private static class DefaultJvmClasses extends AbstractBuildableModelElement implements JvmClasses {
        private final Set<File> resourceDirs = Sets.newHashSet();
        private File classesDir;

        public File getClassesDir() {
            return classesDir;
        }

        public void setClassesDir(File classesDir) {
            this.classesDir = classesDir;
        }

        public Set<File> getResourceDirs() {
            return Sets.newHashSet(resourceDirs);
        }

        public void addResourceDir(File resourceDir) {
            resourceDirs.add(resourceDir);
        }
    }
}
