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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.AbstractBuildableModelElement;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.language.scala.ScalaLanguageSourceSet;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.play.JvmClasses;
import org.gradle.play.PublicAssets;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;
import org.gradle.play.platform.PlayPlatform;

import java.io.File;

public class DefaultPlayApplicationBinarySpec extends BaseBinarySpec implements PlayApplicationBinarySpecInternal {
    private final JvmClasses classesDir = new DefaultJvmClasses();
    private final PublicAssets assets = new DefaultPublicAssets();
    private ScalaLanguageSourceSet generatedScala;
    private PlayPlatform platform;
    private PlayToolChainInternal toolChain;
    private File jarFile;

    @Override
    protected String getTypeName() {
        return "Play Application Jar";
    }

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

    public PublicAssets getAssets() {
        return assets;
    }

    public ScalaLanguageSourceSet getGeneratedScala() {
        return generatedScala;
    }

    public void setGeneratedScala(ScalaLanguageSourceSet scalaSources) {
        this.generatedScala = scalaSources;
    }

    private static class DefaultJvmClasses extends AbstractBuildableModelElement implements JvmClasses {
        private FileCollection resourceDirs = new UnionFileCollection();
        private File classesDir;

        public File getClassesDir() {
            return classesDir;
        }

        public void setClassesDir(File classesDir) {
            this.classesDir = classesDir;
        }

        public FileCollection getResourceDirs() {
            return resourceDirs;
        }

        public void addResourceDir(File resourceDir) {
            resourceDirs.add(new SimpleFileCollection(resourceDir));
        }
    }

    private static class DefaultPublicAssets extends AbstractBuildableModelElement implements PublicAssets {
        private FileCollection resourceDirs = new UnionFileCollection();

        public FileCollection getAssetDirs() {
            return resourceDirs;
        }

        public void addAssetDir(File resourceDir) {
            resourceDirs.add(new SimpleFileCollection(resourceDir));
        }
    }
}
