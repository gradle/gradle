package org.gradle.language.scala.plugins;

import org.gradle.api.DefaultTask;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.scala.ScalaSourceSet;
import org.gradle.language.scala.internal.DefaultScalaSourceSet;
import org.gradle.runtime.base.BinarySpec;
import org.gradle.runtime.base.TransformationFileType;
import org.gradle.runtime.jvm.JvmByteCode;
import org.gradle.runtime.jvm.JvmLibrarySpec;
import org.gradle.runtime.jvm.internal.JvmPlatform;
import org.gradle.runtime.jvm.plugins.JvmComponentPlugin;

import java.util.Collections;
import java.util.Map;

/**
 * Plugin for compiling Scala code. Registers "scala" language support with the {@link org.gradle.language.scala.ScalaSourceSet}
 */
public class ScalaLanguagePlugin implements Plugin<ProjectInternal> {
    public void apply(ProjectInternal project) {
        project.getPlugins().apply(JvmComponentPlugin.class);

        project.getExtensions().getByType(LanguageRegistry.class).add(new Scala());
        project.getExtensions().create("platform", JvmPlatformExtension.class);
        //project.getExtensions().getByType(JvmLibrarySpec.class)
    }

    private static class Scala implements LanguageRegistration<ScalaSourceSet> {

        @Override
        public String getName() {
            return "scala";
        }

        @Override
        public Class<ScalaSourceSet> getSourceSetType() {
            return ScalaSourceSet.class;
        }

        @Override
        public Class<? extends ScalaSourceSet> getSourceSetImplementation() {
            return DefaultScalaSourceSet.class;
        }

        @Override
        public Map<String, Class<?>> getBinaryTools() {
            return Collections.emptyMap();
        }

        @Override
        public Class<? extends TransformationFileType> getOutputType() {
            return JvmByteCode.class;
        }

        @Override
        public SourceTransformTaskConfig getTransformTask() {
            return new SourceTransformTaskConfig() {
                @Override
                public String getTaskPrefix() {
                    return "compile";
                }

                @Override
                public Class<? extends DefaultTask> getTaskType() {
                    return ScalaCompile.class;
                }

                @Override
                public void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet) {
                    ScalaCompile compile = (ScalaCompile) task;
                    ScalaSourceSet scalaSourceSet = (ScalaSourceSet) sourceSet;

//                    JavaSourceSet javaSourceSet = (JavaSourceSet) sourceSet;
//                    JvmLibraryBinarySpec binary = (JvmLibraryBinarySpec) binarySpec;
//
//                    compile.setDescription(String.format("Compiles %s.", javaSourceSet));
//                    compile.setDestinationDir(binary.getClassesDir());
//                    compile.setToolChain(binary.getToolChain());
//
//                    compile.setSource(javaSourceSet.getSource());
//                    compile.setClasspath(javaSourceSet.getCompileClasspath().getFiles());
//                    compile.setSourceCompatibility(JavaVersion.current().toString());
//                    compile.setTargetCompatibility(JavaVersion.current().toString());
//                    compile.setDependencyCacheDir(new File(compile.getProject().getBuildDir(), "jvm-dep-cache"));
//                    compile.dependsOn(javaSourceSet);
//                    binary.getTasks().getJar().dependsOn(compile);


                }
            };
        }

        @Override
        public boolean applyToBinary(BinarySpec binary) {
            return false;
        }
    }

    public static class JvmPlatformExtension { //TODO: naming & move & fix!
        public JvmPlatform scala(String version) {
            return JvmPlatform.scala(version);
        }
    }
}
