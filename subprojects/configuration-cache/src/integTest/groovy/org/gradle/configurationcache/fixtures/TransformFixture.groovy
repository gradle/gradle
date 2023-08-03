/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache.fixtures

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.test.fixtures.file.TestFile

import java.nio.file.Files
import java.util.stream.Collectors

/**
 * A helper to define a plugin that applies a simple transform action to dependencies.
 */
class TransformFixture {
    static final String TRANSFORM_PLUGIN_ID = "org.gradle.test.transform"
    static final String TRANSFORM_SOURCE_CONFIGURATION = "transformSource"

    private final String additionalTransformImports;
    private final String additionalTransformFields;
    private final String additionalTransformImpl;

    TransformFixture(String additionalTransformImports, String additionalTransformFields, String additionalTransformImpl) {
        this.additionalTransformImports = additionalTransformImports
        this.additionalTransformFields = additionalTransformFields
        this.additionalTransformImpl = additionalTransformImpl
    }

    def withTransformPlugin(TestFile pluginDirectory) {
        pluginDirectory.create {
            file("build.gradle") << """
                    plugins {
                        id "java-gradle-plugin"
                    }

                    gradlePlugin {
                        plugins {
                            transformPlugin {
                                id = "$TRANSFORM_PLUGIN_ID"
                                implementationClass = "TransformPlugin"
                            }
                        }
                    }
                """
            file("src/main/java/TransformPlugin.java") << """
                import ${Plugin.name};
                import ${Project.name};
                import ${Configuration.name};
                import ${FileSystemLocation.name};
                import ${Collectors.name};
                import static ${ArtifactTypeDefinition.name}.ARTIFACT_TYPE_ATTRIBUTE;

                public class TransformPlugin implements Plugin<Project> {
                    private static final String JAR_TYPE = "${ArtifactTypeDefinition.JAR_TYPE}";
                    private static final String TXT_TYPE = "txt";

                    @Override
                    public void apply(Project project) {
                        registerTransform(project);
                        Configuration base = createBaseConfiguration(project);
                        Configuration transformed = createTransformedConfiguration(project, base);
                        registerFileCollectionTask(project, transformed);
                        registerProviderTask(project, transformed);
                    }

                    private void registerTransform(Project project) {
                        project.getDependencies().registerTransform(FileSizeTransform.class, spec -> {
                            spec.getFrom().attribute(ARTIFACT_TYPE_ATTRIBUTE, JAR_TYPE);
                            spec.getTo().attribute(ARTIFACT_TYPE_ATTRIBUTE, TXT_TYPE);
                        });
                    }

                    private Configuration createBaseConfiguration(Project project) {
                        return project.getConfigurations().create("$TRANSFORM_SOURCE_CONFIGURATION", cfg -> {
                            cfg.setCanBeConsumed(false);
                            cfg.setCanBeResolved(true);
                        });
                    }

                    private Configuration createTransformedConfiguration(Project project, Configuration base) {
                        return project.getConfigurations().create("transformed", transformed -> {
                            transformed.setCanBeConsumed(false);
                            transformed.setCanBeResolved(true);
                            transformed.extendsFrom(base);

                            transformed.getAttributes().attribute(ARTIFACT_TYPE_ATTRIBUTE, TXT_TYPE);
                        });
                    }

                    private void registerFileCollectionTask(Project project, Configuration transformed) {
                        project.getTasks().register("resolveCollection", ResolveTask.class, task -> {
                            task.getInputSizes().setFrom(transformed);
                        });
                    }

                    private void registerProviderTask(Project project, Configuration transformed) {
                        project.getTasks().register("resolveProviders", ResolveProviderTask.class, task -> {
                            task.getInputFiles().set(
                                    // Mapped provider is evaluated differently when storing into the configuration cache.
                                    // This is similar to what AGP does when performing JdkImageTransform
                                    transformed.getElements().map(
                                            locations -> locations.stream()
                                                    .map(FileSystemLocation::getAsFile)
                                                    .collect(Collectors.toList())
                                    )
                            );
                        });
                    }
                }
            """
            file("src/main/java/ResolveTask.java") << """
                import ${DefaultTask.name};
                import ${ConfigurableFileCollection.name};
                import ${InputFiles.name};
                import ${PathSensitive.name};
                import ${PathSensitivity.name};
                import ${TaskAction.name};

                public abstract class ResolveTask extends DefaultTask {
                    @InputFiles
                    @PathSensitive(PathSensitivity.NONE)
                    public abstract ConfigurableFileCollection getInputSizes();

                    @TaskAction
                    public void action() {
                        getInputSizes().getFiles().forEach(file -> {
                            System.out.println(file.getName());
                        });
                    }
                }
            """
            file("src/main/java/ResolveProviderTask.java") << """
                import ${DefaultTask.name};
                import ${ListProperty.name};
                import ${InputFiles.name};
                import ${PathSensitive.name};
                import ${PathSensitivity.name};
                import ${TaskAction.name};
                import ${File.name};

                public abstract class ResolveProviderTask extends DefaultTask {
                    @InputFiles
                    @PathSensitive(PathSensitivity.NAME_ONLY)
                    public abstract ListProperty<File> getInputFiles();

                    @TaskAction
                    public void action() {
                        getInputFiles().get().forEach(file -> System.out.println(file.getName()));
                    }
                }
            """

            file("src/main/java/FileSizeTransform.java") << """
                import ${InputArtifact.name};
                import ${TransformAction.name};
                import ${TransformOutputs.name};
                import ${TransformParameters.name};
                import ${FileSystemLocation.name};
                import ${Provider.name};

                import ${File.name};
                import ${Files.name};
                import ${Collections.name};

                $additionalTransformImports

                public abstract class FileSizeTransform implements TransformAction<TransformParameters.None> {
                    @InputArtifact
                    public abstract Provider<FileSystemLocation> getInputArtifact();

                    $additionalTransformFields

                    @Override
                    public void transform(TransformOutputs outputs) {
                        File input = getInputArtifact().get().getAsFile();
                        File output = outputs.file(input.getName() + ".txt");

                        try {
                            Files.write(output.toPath(), Collections.singleton(String.valueOf(input.length())));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                        $additionalTransformImpl
                    }
                }
            """
        }
    }

    def withJavaLibrarySubproject(TestFile projectDir) {
        projectDir.create {
            file("build.gradle") << """
                plugins { id("java-library") }
            """

            file("src/main/java/Foo.java") << """
                public class Foo {}
            """
        }
    }
}
