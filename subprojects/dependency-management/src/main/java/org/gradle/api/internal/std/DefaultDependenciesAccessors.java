/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.std;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.initialization.DependenciesAccessors;
import org.gradle.internal.Cast;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringWriter;
import java.util.List;

public class DefaultDependenciesAccessors implements DependenciesAccessors {
    private final static String ACCESSORS_PACKAGE = "org.gradle.accessors.dm";
    private final static String ACCESSORS_CLASSNAME = "Libraries";

    private final ClassPath classPath;
    private final DependenciesAccessorsWorkspace workspace;
    private final ProviderFactory providerFactory;
    private final ObjectFactory objects;
    private AllDependenciesModel dependenciesConfiguration;
    private Class<? extends ExternalModuleDependencyFactory> factory;
    private ClassPath sources = DefaultClassPath.of();
    private ClassPath classes = DefaultClassPath.of();

    public DefaultDependenciesAccessors(ClassPathRegistry registry, DependenciesAccessorsWorkspace workspace, ProviderFactory providerFactory, ObjectFactory objects) {
        this.classPath = registry.getClassPath("DEPENDENCIES-EXTENSION-COMPILER");
        this.workspace = workspace;
        this.providerFactory = providerFactory;
        this.objects = objects;
    }

    @Override
    public void generateAccessors(File source, ClassLoaderScope classLoaderScope) {
        try {
            RegularFileProperty srcProp = objects.fileProperty();
            srcProp.set(source);
            Provider<byte[]> dataSource = providerFactory.fileContents(srcProp).getAsBytes().forUseAtConfigurationTime();
            DependenciesFileParser parser = new DependenciesFileParser();
            dependenciesConfiguration = parser.parse(new ByteArrayInputStream(dataSource.get()));
            StringWriter writer = new StringWriter();
            Hasher hash = Hashing.sha1().newHasher();
            List<String> dependencyAliases = dependenciesConfiguration.getDependencyAliases();
            List<String> bundles = dependenciesConfiguration.getBundleAliases();
            dependencyAliases.forEach(hash::putString);
            bundles.forEach(hash::putString);
            String keysHash = hash.hash().toString();
            factory = workspace.withWorkspace(keysHash, (workspace, executionHistoryStore) -> {
                File srcDir = new File(workspace, "sources");
                File dstDir = new File(workspace, "classes");
                if (!srcDir.exists() || !dstDir.exists()) {
                    DependenciesSourceGenerator.generateSource(writer, source, dependenciesConfiguration, ACCESSORS_PACKAGE, ACCESSORS_CLASSNAME);
                    DependenciesClassGenerator.compile(srcDir, dstDir, ACCESSORS_PACKAGE, ACCESSORS_CLASSNAME, writer.toString(), classPath);
                }
                sources = DefaultClassPath.of(srcDir);
                classes = DefaultClassPath.of(dstDir);
                classLoaderScope.export(DefaultClassPath.of(dstDir));
                Class<? extends ExternalModuleDependencyFactory> clazz;
                try {
                    clazz = Cast.uncheckedCast(classLoaderScope.getExportClassLoader().loadClass(ACCESSORS_PACKAGE + "." + ACCESSORS_CLASSNAME));
                } catch (ClassNotFoundException e) {
                    return null;
                }
                return clazz;
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void createExtension(ExtensionContainer container) {
        if (dependenciesConfiguration != null) {
            container.create("libs", factory, dependenciesConfiguration);
        }
    }

    @Override
    public ClassPath getSources() {
        return classes;
    }

    @Override
    public ClassPath getClasses() {
        return sources;
    }
}
