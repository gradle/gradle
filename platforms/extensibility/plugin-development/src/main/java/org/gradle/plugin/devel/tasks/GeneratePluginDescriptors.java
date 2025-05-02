/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.devel.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.util.PropertiesUtils;
import org.gradle.plugin.devel.PluginDeclaration;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Generates plugin descriptors from plugin declarations.
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class GeneratePluginDescriptors extends DefaultTask {

    private final ListProperty<PluginDeclaration> declarations;
    private final Provider<Map<String, String>> implementationClassById;
    private final DirectoryProperty outputDirectory;

    public GeneratePluginDescriptors() {
        ObjectFactory objectFactory = getProject().getObjects();
        declarations = objectFactory.listProperty(PluginDeclaration.class);
        outputDirectory = objectFactory.directoryProperty();
        implementationClassById = getDeclarations().map(declarations -> declarations.stream()
            .collect(Collectors.toMap(PluginDeclaration::getId, PluginDeclaration::getImplementationClass, (a, b) -> b, LinkedHashMap::new))
        );
    }

    /**
     * The plugin declarations used to create the descriptors.
     */
    @Internal("Changes for the declarations are tracked via implementationClassById")
    public ListProperty<PluginDeclaration> getDeclarations() {
        return declarations;
    }

    /**
     * Returns all {@code (id, implementation class)} pairs from {@link #getDeclarations()}.
     * <p>
     * This map is the only input needed from the plugin declarations to create the plugin descriptors.
     */
    @Input
    Provider<Map<String, String>> getImplementationClassById() {
        return implementationClassById;
    }

    @OutputDirectory
    public DirectoryProperty getOutputDirectory() {
        return outputDirectory;
    }

    @TaskAction
    public void generatePluginDescriptors() {
        File outputDir = outputDirectory.get().getAsFile();
        clearOutputDirectory(outputDir);
        for (Map.Entry<String, String> entry : implementationClassById.get().entrySet()) {
            String id = entry.getKey();
            String implementationClass = entry.getValue();
            File descriptorFile = new File(outputDir, id + ".properties");
            Properties properties = new Properties();
            properties.setProperty("implementation-class", implementationClass);
            writePropertiesTo(properties, descriptorFile);
        }
    }

    @Inject
    protected Deleter getDeleter() {
        throw new UnsupportedOperationException("Decorator takes care of injection");
    }

    private void clearOutputDirectory(File directoryToClear) {
        try {
            getDeleter().ensureEmptyDirectory(directoryToClear);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writePropertiesTo(Properties properties, File descriptorFile) {
        try {
            PropertiesUtils.store(properties, descriptorFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
