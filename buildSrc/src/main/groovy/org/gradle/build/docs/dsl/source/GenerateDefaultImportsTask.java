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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.build.docs.dsl.source.model.ClassMetaData;
import org.gradle.build.docs.model.SimpleClassMetaDataRepository;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

@CacheableTask
public class GenerateDefaultImportsTask extends DefaultTask {
    private File metaDataFile;
    private File importsDestFile;
    private File mappingDestFile;
    private Set<String> excludePatterns = new LinkedHashSet<String>();
    private Set<String> extraPackages = new LinkedHashSet<String>();

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    public File getMetaDataFile() {
        return metaDataFile;
    }

    public void setMetaDataFile(File metaDataFile) {
        this.metaDataFile = metaDataFile;
    }

    @OutputFile
    public File getImportsDestFile() {
        return importsDestFile;
    }

    public void setImportsDestFile(File importsDestFile) {
        this.importsDestFile = importsDestFile;
    }

    @OutputFile
    public File getMappingDestFile() {
        return mappingDestFile;
    }

    public void setMappingDestFile(File destFile) {
        this.mappingDestFile = destFile;
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

    @Input
    public Set<String> getExtraPackages() {
        return extraPackages;
    }

    public void setExtraPackages(Set<String> extraPackages) {
        this.extraPackages = extraPackages;
    }

    public void extraPackage(String name) {
        extraPackages.add(name);
    }

    @TaskAction
    public void generate() throws IOException {
        SimpleClassMetaDataRepository<ClassMetaData> repository = new SimpleClassMetaDataRepository<ClassMetaData>();
        repository.load(getMetaDataFile());

        final Set<String> excludedPrefixes = new HashSet<String>();
        final Set<String> excludedPackages = new HashSet<String>();
        for (String excludePattern : excludePatterns) {
            if (excludePattern.endsWith(".**")) {
                String baseName = excludePattern.substring(0, excludePattern.length() - 3);
                excludedPrefixes.add(baseName + '.');
                excludedPackages.add(baseName);
            } else {
                excludedPackages.add(excludePattern);
            }
        }
        final Set<String> packages = new TreeSet<String>();
        packages.addAll(extraPackages);
        final Multimap<String, String> simpleNames = HashMultimap.create();

        repository.each(new Action<ClassMetaData>() {
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

        final PrintWriter mappingFileWriter = new PrintWriter(new FileWriter(getMappingDestFile()));
        try {
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
        } finally {
            mappingFileWriter.close();
        }

        final PrintWriter writer = new PrintWriter(new FileWriter(getImportsDestFile()));
        try {
            for (String packageName : packages) {
                writer.print("import ");
                writer.print(packageName);
                writer.println(".*");
            }
        } finally {
            writer.close();
        }
    }
}
