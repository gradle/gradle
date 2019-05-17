/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.build.docs.dsl.source;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.build.docs.dsl.source.model.ClassMetaData;
import org.gradle.build.docs.model.SimpleClassMetaDataRepository;

import javax.inject.Inject;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@NonNullApi
@CacheableTask
public class GenerateDefaultImportsTask extends DefaultTask {
    private RegularFileProperty metaDataFile;
    private RegularFileProperty importsDestFile;
    private RegularFileProperty mappingDestFile;
    private Set<String> excludePatterns = new LinkedHashSet<>();

    @Inject
    public GenerateDefaultImportsTask(ObjectFactory objectFactory) {
        metaDataFile = objectFactory.fileProperty();
        importsDestFile = objectFactory.fileProperty();
        mappingDestFile = objectFactory.fileProperty();
    }

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    public RegularFileProperty getMetaDataFile() {
        return metaDataFile;
    }

    @OutputFile
    public RegularFileProperty getImportsDestFile() {
        return importsDestFile;
    }

    @OutputFile
    public RegularFileProperty getMappingDestFile() {
        return mappingDestFile;
    }

    @Input
    public Set<String> getExcludedPackages() {
        return excludePatterns;
    }

    public void setExcludedPackages(Set<String> excludedPackages) {
        this.excludePatterns = excludedPackages;
    }

    /**
     * Package name can end with '.**' to exclude subpackages as well.
     */
    public void excludePackage(String name) {
        excludePatterns.add(name);
    }

    @TaskAction
    public void generate() throws IOException {
        SimpleClassMetaDataRepository<ClassMetaData> repository = new SimpleClassMetaDataRepository<>();
        repository.load(getMetaDataFile().getAsFile().get());

        final Set<String> excludedPrefixes = new HashSet<>();
        final Set<String> excludedPackages = new HashSet<>();
        for (String excludePattern : excludePatterns) {
            if (excludePattern.endsWith(".**")) {
                String baseName = excludePattern.substring(0, excludePattern.length() - 3);
                excludedPrefixes.add(baseName + '.');
                excludedPackages.add(baseName);
            } else {
                excludedPackages.add(excludePattern);
            }
        }
        final Set<String> packages = new LinkedHashSet<>();
        final Multimap<String, String> simpleNames = LinkedHashMultimap.create();

        repository.each(new Action<ClassMetaData>() {
            @Override
            public void execute(ClassMetaData classMetaData) {
                if (classMetaData.getOuterClassName() != null) {
                    // Ignore inner classes
                    return;
                }
                String packageName = classMetaData.getPackageName();
                if (excludedPackages.contains(packageName)) {
                    return;
                }
                for (String excludedPrefix : excludedPrefixes) {
                    if (packageName.startsWith(excludedPrefix)) {
                        return;
                    }
                }
                simpleNames.put(classMetaData.getSimpleName(), classMetaData.getClassName());
                packages.add(packageName);
            }
        });

        try (PrintWriter mappingFileWriter = new PrintWriter(new FileWriter(getMappingDestFile().getAsFile().get()))) {
            for (Map.Entry<String, Collection<String>> entry : simpleNames.asMap().entrySet()) {
                if (entry.getValue().size() > 1) {
                    System.out.println(String.format("Multiple DSL types have short name '%s'", entry.getKey()));
                    for (String className : entry.getValue()) {
                        System.out.println("    * " + className);
                    }
                }
                mappingFileWriter.print(entry.getKey());
                mappingFileWriter.print(":");
                for (String className : entry.getValue()) {
                    mappingFileWriter.print(className);
                    mappingFileWriter.print(";");
                }
                mappingFileWriter.println();
            }
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(getImportsDestFile().getAsFile().get()))) {
            for (String packageName : packages) {
                writer.print("import ");
                writer.print(packageName);
                writer.println(".*");
            }
        }
    }
}
