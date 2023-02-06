/*
 * Copyright 2022 the original author or authors.
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

package gradlebuild;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.DefaultClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.api.provider.Provider;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.OutputStreamBackedEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public abstract class AnalyzingArtifactTransform implements TransformAction<TransformParameters.None> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyzingArtifactTransform.class);

    private final StringInterner interner = new StringInterner();

    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        File input = getInputArtifact().get().getAsFile();

        Map<String, ClassAnalysisWrapper> classNameToSource = new HashMap<>();
        Consumer<ClassSource> classStreamAcceptor = source -> {
            ClassAnalysis analysis = analyzeClassFile(source.data);

            String className = analysis.getClassName();
            String newSource = source.path;

            ClassAnalysisWrapper oldSource;
            if ((oldSource = classNameToSource.get(className)) != null) {

                // TODO: Detect other cases. Eg. if old is META-INF check that new is META-INF
                if (oldSource.path.startsWith("META-INF/versions")) {
                    // Assume new source is not a multi-release class. Override old.
                    classNameToSource.put(className, new ClassAnalysisWrapper(analysis, newSource));
                } else {
                    // New source must be multi-release (maybe, probably).
                    LOGGER.debug("Did not replace " + oldSource + " with " + newSource);
                }
            } else {
                classNameToSource.put(className, new ClassAnalysisWrapper(analysis, newSource));
            }
        };

        if (input.isDirectory()) {
            walkClassesDir(input, classStreamAcceptor);
        } else {
            walkJarClasses(input, classStreamAcceptor);
        }

        DependencyAnalysis analysis = new DependencyAnalysis(classNameToSource.values().stream().map(x -> x.analysis).collect(Collectors.toList()));
        File outputFile = outputs.file(input.getName() + ".analysis");

        try {
            Encoder encoder = new OutputStreamBackedEncoder(Files.newOutputStream(outputFile.toPath()));
            new DependencyAnalysis.Serializer(interner).write(encoder, analysis);
        } catch (Exception e) {
            throw new GradleException("Error writing dependency output file", e);
        }
    }

    public ClassAnalysis analyzeClassFile(InputStream classData) {
        try {
            // TODO: Not sure we want to scan the same way. This scanner is too lenient.
            // TODO: We don't get enough data from this analyzer.
            // It would be nice to track which methods are called on referenced classes.
            return new DefaultClassDependenciesAnalyzer(interner).getClassAnalysis(classData);
        } catch (IOException e) {
            throw new GradleException("Error scanning inputs", e);
        }
    }

    private void walkClassesDir(File classDir, Consumer<ClassSource> classStreamAcceptor) {
        try (Stream<Path> walker = Files.walk(classDir.toPath())) {
            walker.forEach((Path file) -> {
                if (!Files.isDirectory(file) && file.getFileName().toString().endsWith(".class")) {
                    try {
                        classStreamAcceptor.accept(new ClassSource(classDir.toPath().relativize(file.toAbsolutePath()).toString(), Files.newInputStream(file)));
                    } catch (IOException e) {
                        throw new GradleException("Cannot read class file", e);
                    }
                }
            });
        } catch (IOException e) {
            throw new GradleException("Error walking classes dir for dependency analysis: " + classDir.getName(), e);
        }
    }

    private void walkJarClasses(File jar, Consumer<ClassSource> classStreamAcceptor) {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar.toPath()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    classStreamAcceptor.accept(new ClassSource(entry.getName(), zis));
                }
            }
        } catch (IOException e) {
            throw new GradleException("Error unzipping zip for dependency analysis: " + jar.getName(), e);
        }
    }

    public static class DependencyAnalysis {

        private final List<ClassAnalysis> classes;

        public DependencyAnalysis(List<ClassAnalysis> classes) {
            this.classes = classes;
        }

        public List<ClassAnalysis> getClasses() {
            return classes;
        }

        public static class Serializer implements org.gradle.internal.serialize.Serializer<DependencyAnalysis> {

            private final ClassAnalysis.Serializer serializer;

            public Serializer(StringInterner interner) {
                this.serializer = new ClassAnalysis.Serializer(interner);
            }

            @Override
            public DependencyAnalysis read(Decoder decoder) throws Exception {
                int num = decoder.readInt();

                if (num == 0x504B0304) {
                    // This is a Zip File.
                    throw new RuntimeException("Classes not analyzed by artifact transform.");
                }

                List<ClassAnalysis> classes = new ArrayList<>(num);
                for (int i = 0; i < num; i++) {
                    classes.add(serializer.read(decoder));
                }
                return new DependencyAnalysis(classes);
            }

            @Override
            public void write(Encoder encoder, DependencyAnalysis value) throws Exception {
                encoder.writeInt(value.classes.size());
                for (ClassAnalysis classAnalysis : value.classes) {
                       serializer.write(encoder, classAnalysis);
                }
            }
        }
    }

    private static final class ClassSource {
        private final String path;
        private final InputStream data;
        public ClassSource(String path, InputStream data) {
            this.path = path;
            this.data = data;
        }
    }

    private static final class ClassAnalysisWrapper {
        private final ClassAnalysis analysis;
        private final String path;
        public ClassAnalysisWrapper(ClassAnalysis analysis, String path) {
            this.analysis = analysis;
            this.path = path;
        }
    }
}
