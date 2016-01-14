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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Nullable;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.AbstractBuildableModelElement;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.jvm.internal.JvmAssembly;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.language.javascript.JavaScriptSourceSet;
import org.gradle.language.javascript.internal.DefaultJavaScriptSourceSet;
import org.gradle.language.scala.ScalaLanguageSourceSet;
import org.gradle.language.scala.internal.DefaultScalaJvmAssembly;
import org.gradle.language.scala.internal.DefaultScalaLanguageSourceSet;
import org.gradle.language.scala.internal.ScalaJvmAssembly;
import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.platform.base.internal.BinaryBuildAbility;
import org.gradle.platform.base.internal.ToolSearchBuildAbility;
import org.gradle.play.JvmClasses;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.PublicAssets;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;
import org.gradle.play.platform.PlayPlatform;

import java.io.File;
import java.util.Map;
import java.util.Set;

import static org.gradle.util.CollectionUtils.single;

public class DefaultPlayApplicationBinarySpec extends BaseBinarySpec implements PlayApplicationBinarySpecInternal {
    private final DefaultScalaJvmAssembly jvmAssembly = new DefaultScalaJvmAssembly();
    private final PublicAssets assets = new DefaultPublicAssets();
    private Map<LanguageSourceSet, ScalaLanguageSourceSet> generatedScala = Maps.newHashMap();
    private Map<LanguageSourceSet, JavaScriptSourceSet> generatedJavaScript = Maps.newHashMap();
    private PlayPlatform platform;
    private PlayToolChainInternal toolChain;
    private File jarFile;
    private File assetsJarFile;
    private FileCollection classpath;

    @Override
    protected String getTypeName() {
        return "Play Application Jar";
    }

    @Override
    public PlayApplicationSpec getApplication() {
        return getComponentAs(PlayApplicationSpec.class);
    }

    public PlayPlatform getTargetPlatform() {
        return platform;
    }

    public PlayToolChainInternal getToolChain() {
        return toolChain;
    }

    public ScalaJvmAssembly getAssembly() {
        return jvmAssembly;
    }

    public File getJarFile() {
        return jarFile;
    }

    public void setTargetPlatform(PlayPlatform platform) {
        this.platform = platform;
        jvmAssembly.setTargetPlatform(platform.getJavaPlatform());
        jvmAssembly.setScalaPlatform(platform.getScalaPlatform());
    }

    public void setToolChain(PlayToolChainInternal toolChain) {
        this.toolChain = toolChain;
    }

    public void setJarFile(File file) {
        this.jarFile = file;
    }

    public File getAssetsJarFile() {
        return assetsJarFile;
    }

    public void setAssetsJarFile(File assetsJarFile) {
        this.assetsJarFile = assetsJarFile;
    }

    public JvmClasses getClasses() {
        return new JvmClassesAdapter(jvmAssembly);
    }

    public PublicAssets getAssets() {
        return assets;
    }

    @Override
    public Map<LanguageSourceSet, ScalaLanguageSourceSet> getGeneratedScala() {
        return generatedScala;
    }

    @Override
    public void addGeneratedScala(LanguageSourceSet input, SourceDirectorySetFactory sourceDirectorySetFactory) {
        String lssName = String.format("%sScalaSources", input.getName());
        // TODO:DAZ To get rid of this, we need a `FunctionalSourceSet` instance here, and that's surprisingly difficult to get.
        ScalaLanguageSourceSet generatedScalaSources = BaseLanguageSourceSet.create(ScalaLanguageSourceSet.class, DefaultScalaLanguageSourceSet.class, lssName, getName(), sourceDirectorySetFactory);
        generatedScalaSources.builtBy();
        generatedScala.put(input, generatedScalaSources);
    }

    @Override
    public Map<LanguageSourceSet, JavaScriptSourceSet> getGeneratedJavaScript() {
        return generatedJavaScript;
    }

    @Override
    public void addGeneratedJavaScript(LanguageSourceSet input, SourceDirectorySetFactory sourceDirectorySetFactory) {
        String lssName = String.format("%sJavaScript", input.getName());
        JavaScriptSourceSet javaScript = BaseLanguageSourceSet.create(JavaScriptSourceSet.class, DefaultJavaScriptSourceSet.class, lssName, getName(), sourceDirectorySetFactory);
        javaScript.builtBy();
        generatedJavaScript.put(input, javaScript);
    }

    @Override
    public FileCollection getClasspath() {
        return classpath;
    }

    @Override
    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    @Override
    public BinaryBuildAbility getBinaryBuildAbility() {
        return new ToolSearchBuildAbility(getToolChain().select(getTargetPlatform()));
    }

    @Override
    public boolean hasCodependentSources() {
        return true;
    }

    private static class JvmClassesAdapter implements JvmClasses {

        private final JvmAssembly jvmAssembly;

        private JvmClassesAdapter(JvmAssembly jvmAssembly) {
            this.jvmAssembly = jvmAssembly;
        }

        public File getClassesDir() {
            return single(jvmAssembly.getClassDirectories());
        }

        public void setClassesDir(File classesDir) {
            replaceSingleDirectory(jvmAssembly.getClassDirectories(), classesDir);
        }

        public Set<File> getResourceDirs() {
            return jvmAssembly.getResourceDirectories();
        }

        public void addResourceDir(File resourceDir) {
            jvmAssembly.getResourceDirectories().add(resourceDir);
        }

        public void builtBy(Object... tasks) {
            jvmAssembly.builtBy(tasks);
        }

        @Nullable
        public Task getBuildTask() {
            return jvmAssembly.getBuildTask();
        }

        public void setBuildTask(Task lifecycleTask) {
            jvmAssembly.setBuildTask(lifecycleTask);
        }

        public boolean hasBuildDependencies() {
            return jvmAssembly.hasBuildDependencies();
        }

        public TaskDependency getBuildDependencies() {
            return jvmAssembly.getBuildDependencies();
        }
    }

    private static class DefaultPublicAssets extends AbstractBuildableModelElement implements PublicAssets {
        private Set<File> resourceDirs = Sets.newLinkedHashSet();

        public Set<File> getAssetDirs() {
            return resourceDirs;
        }

        public void addAssetDir(File assetDir) {
            resourceDirs.add(assetDir);
        }
    }
}
