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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public abstract class AnalyzingArtifactTransform implements TransformAction<TransformParameters.None> {

    private final StringInterner interner = new StringInterner();

    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        File input = getInputArtifact().get().getAsFile();

        List<ClassAnalysis> analysesList = new ArrayList<>();
        Consumer<InputStream> classStreamAcceptor =
            stream -> analysesList.add(analyzeClassFile(stream));

        if (input.isDirectory()) {
            walkClassesDir(input, classStreamAcceptor);
        } else {
            walkJarClasses(input, classStreamAcceptor);
        }

        DependencyAnalysis analysis = new DependencyAnalysis(analysesList);
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
            return new DefaultClassDependenciesAnalyzer(interner).getClassAnalysis(classData);
        } catch (IOException e) {
            throw new GradleException("Error scanning inputs", e);
        }
    }

    private void walkClassesDir(File classDir, Consumer<InputStream> classStreamAcceptor) {
        try (Stream<Path> walker = Files.walk(classDir.toPath())) {
            walker.forEach((Path file) -> {
                if (!Files.isDirectory(file) && file.getFileName().toString().endsWith(".class")) {
                    try {
                        classStreamAcceptor.accept(Files.newInputStream(file));
                    } catch (IOException e) {
                        throw new GradleException("Cannot read class file", e);
                    }
                }
            });
        } catch (IOException e) {
            throw new GradleException("Error walking classes dir for dependency analysis: " + classDir.getName(), e);
        }
    }

    private void walkJarClasses(File jar, Consumer<InputStream> classStreamAcceptor) {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar.toPath()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    classStreamAcceptor.accept(zis);
                }
            }
        } catch (IOException e) {
            throw new GradleException("Error unzipping zip for dependency analysis: " + jar.getName(), e);
        }
    }

    public static class DependencyAnalysis {

        private final List<ClassAnalysis> analyses;

        public DependencyAnalysis(List<ClassAnalysis> analyses) {
            this.analyses = analyses;
        }

        public List<ClassAnalysis> getAnalyses() {
            return analyses;
        }

        public static class Serializer implements org.gradle.internal.serialize.Serializer<DependencyAnalysis> {

            private final ClassAnalysis.Serializer serializer;

            public Serializer(StringInterner interner) {
                this.serializer = new ClassAnalysis.Serializer(interner);
            }

            @Override
            public DependencyAnalysis read(Decoder decoder) throws Exception {
                int num = decoder.readInt();

                List<ClassAnalysis> analyses = new ArrayList<>(num);
                for (int i = 0; i < num; i++) {
                    analyses.add(serializer.read(decoder));
                }
                return new DependencyAnalysis(analyses);
            }

            @Override
            public void write(Encoder encoder, DependencyAnalysis value) throws Exception {
                encoder.writeInt(value.analyses.size());
                for (ClassAnalysis classAnalysis : value.analyses) {
                       serializer.write(encoder, classAnalysis);
                }
            }
        }
    }
}
