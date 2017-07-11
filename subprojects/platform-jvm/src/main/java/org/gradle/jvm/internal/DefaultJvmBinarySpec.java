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

package org.gradle.jvm.internal;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.language.base.DependentSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.SourceComponentSpec;
import org.gradle.platform.base.binary.BaseBinarySpec;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.List;

import static org.gradle.util.CollectionUtils.findSingle;

public class DefaultJvmBinarySpec extends BaseBinarySpec implements JvmBinarySpecInternal {
    private final DefaultJvmAssembly assembly = new DefaultJvmAssembly(getIdentifier().child("assembly"));

    @Override
    public JavaToolChain getToolChain() {
        return assembly.getToolChain();
    }

    @Override
    public void setToolChain(JavaToolChain toolChain) {
        assembly.setToolChain(toolChain);
    }

    @Override
    public JavaPlatform getTargetPlatform() {
        return assembly.getTargetPlatform();
    }

    @Override
    public void setTargetPlatform(JavaPlatform platform) {
        assembly.setTargetPlatform(platform);
    }

    @Override
    public File getClassesDir() {
        return findSingle(assembly.getClassDirectories());
    }

    @Override
    public void setClassesDir(File classesDir) {
        replaceSingleDirectory(assembly.getClassDirectories(), classesDir);
    }

    @Override
    public File getResourcesDir() {
        return findSingle(assembly.getResourceDirectories());
    }

    @Override
    public void setResourcesDir(File resourcesDir) {
        replaceSingleDirectory(assembly.getResourceDirectories(), resourcesDir);
    }

    public JvmAssembly getAssembly() {
        return assembly;
    }

    public static List<DependencySpec> collectDependencies(final BinarySpec binary, @Nullable final SourceComponentSpec owner, final Collection<DependencySpec>... specificDependencies) {
        List<DependencySpec> dependencies = Lists.newArrayList();
        if (specificDependencies!=null) {
            for (Collection<DependencySpec> deps : specificDependencies) {
                dependencies.addAll(deps);
            }
        }
        Collection<LanguageSourceSet> binarySources = binary.getSources().values();
        Iterable<LanguageSourceSet> sourceSets = owner != null ? Iterables.concat(owner.getSources().values(), binarySources) : binarySources;
        for (LanguageSourceSet sourceSet : sourceSets) {
            if (sourceSet instanceof DependentSourceSet) {
                dependencies.addAll(((DependentSourceSet) sourceSet).getDependencies().getDependencies());
            }
        }
        return dependencies;
    }
}
