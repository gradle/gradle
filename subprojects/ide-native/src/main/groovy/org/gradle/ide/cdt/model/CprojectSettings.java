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
package org.gradle.ide.cdt.model;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import groovy.util.Node;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.cpp.CppSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.nativeplatform.NativeExecutableSpec;
import org.gradle.nativeplatform.NativeLibrarySpec;
import org.gradle.platform.base.SourceComponentSpec;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes a more logical view of the actual .cproject descriptor file
 */
@Incubating
public class CprojectSettings {

    private NativeComponentSpec binary;
    private final ConfigurableFileCollection includeRoots;
    private final ConfigurableFileCollection libs;

    public CprojectSettings(NativeComponentSpec binary, ProjectInternal project) {
        this.binary = binary;
        includeRoots = project.files();
        libs = project.files();
        if (binary instanceof SourceComponentSpec) {
            SourceComponentSpec sourceComponentSpec = (SourceComponentSpec) binary;
            sourceComponentSpec.getSources().withType(HeaderExportingSourceSet.class).all(new Action<HeaderExportingSourceSet>() {
                @Override
                public void execute(HeaderExportingSourceSet sourceSet) {
                    includeRoots.builtBy(sourceSet.getExportedHeaders()); // have to manually add because we use srcDirs in from, not the real collection
                    includeRoots.from(sourceSet.getExportedHeaders().getSrcDirs());
                }
            });

            sourceComponentSpec.getSources().withType(CppSourceSet.class).all(new Action<CppSourceSet>() {
                @Override
                public void execute(CppSourceSet sourceSet) {
                    for (Object lib : sourceSet.getLibs()) {
                        if (lib instanceof NativeDependencySet) {
                            libs.from(((NativeDependencySet) lib).getLinkFiles());
                            includeRoots.from(((NativeDependencySet) lib).getIncludeRoots());
                        }
                    }

                }
            });
        }

    }

    public FileCollection getIncludeRoots() {
        return includeRoots;
    }

    public FileCollection getLibs() {
        return libs;
    }

    public NativeComponentSpec getBinary() {
        return binary;
    }

    public void setBinary(NativeComponentSpec binary) {
        this.binary = binary;
    }

    public void applyTo(CprojectDescriptor descriptor) {
        if (binary == null) {
            throw new IllegalStateException("no binary set");
        } else {
            applyBinaryTo(descriptor);
        }

    }

    @SuppressWarnings("unchecked")
    private void applyBinaryTo(final CprojectDescriptor descriptor) {
        for (Object compiler : descriptor.getRootCppCompilerTools()) {
            Node includePathsOption = descriptor.getOrCreateIncludePathsOption((Node) compiler);
            for (Object includePath : Lists.newArrayList(includePathsOption.children())) {
                includePathsOption.remove((Node) includePath);
            }
            for (File includeRoot : includeRoots) {
                Map<String, String> map = new LinkedHashMap<String, String>(2);
                map.put("builtIn", "false");
                map.put("value", includeRoot.getAbsolutePath());
                includePathsOption.appendNode("listOptionValue", map);
            }
        }

        for (Object linker  : descriptor.getRootCppLinkerTools()) {
            Node libsOption = descriptor.getOrCreateLibsOption((Node) linker);
            for (Object lib : Lists.newArrayList(libsOption.children())) {
                libsOption.remove((Node) lib);
            }
            for (File lib : libs) {
                Map<String, String> map = new LinkedHashMap<String, String>(2);
                map.put("builtIn", "false");
                map.put("value", lib.getAbsolutePath());
                libsOption.appendNode("listOptionValue", map);

            }
        }

        final String extension = "";
        String type;
        if (binary instanceof NativeLibrarySpec) {
            type = "org.eclipse.cdt.build.core.buildArtefactType.sharedLib";
        } else if (binary instanceof NativeExecutableSpec) {
            type = "org.eclipse.cdt.build.core.buildArtefactType.exe";
        } else {
            throw new IllegalStateException("The binary " + binary + " is of a type that we don\'t know about");
        }

        for (Node conf : (List<Node>) descriptor.getConfigurations()) {
            conf.attributes().put("buildArtefactType", type);
            conf.attributes().put("artifactExtension", extension);
            String[] buildPropsPairs = ((String) conf.attributes().get("buildProperties")).split(",");
            Map<String, String> buildProps = Maps.newLinkedHashMap();
            for (String buildPropsPair : buildPropsPairs) {
                String[] parts = buildPropsPair.split("=", 2);
                buildProps.put(parts[0], parts[1]);
            }
            buildProps.put("org.eclipse.cdt.build.core.buildArtefactType", type);
            buildPropsPairs = Iterables.toArray(Iterables.transform(buildProps.entrySet(), new Function<Map.Entry, String>() {
                @Override
                public String apply(Map.Entry entry) {
                    return String.valueOf(entry.getKey()) + "=" + String.valueOf(entry.getValue());
                }
            }), String.class);
            conf.attributes().put("buildProperties", DefaultGroovyMethods.join(buildPropsPairs, ","));
        }
    }
}
