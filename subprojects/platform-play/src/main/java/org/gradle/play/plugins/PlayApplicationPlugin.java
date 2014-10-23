/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.play.plugins;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.*;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.platform.base.*;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.internal.DefaultPlayApplicationBinarySpec;
import org.gradle.play.internal.DefaultPlayApplicationSpec;
import org.gradle.play.internal.DefaultPlayToolChain;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;

import java.io.File;

/**
 * Plugin for Play Framework component support.
 * Registers the {@link org.gradle.play.PlayApplicationSpec} component type for
 * the {@link org.gradle.platform.base.ComponentSpecContainer}.
 */
@Incubating
public class PlayApplicationPlugin implements Plugin<ProjectInternal> {
    public static final String DEFAULT_PLAY_VERSION = "2.3.5";
    public static final String DEFAULT_SCALA_VERSION = "2.11.1";

    public void apply(ProjectInternal project) {
    }

    /**
     * Model rules.
     */
    @SuppressWarnings("UnusedDeclaration")
    @RuleSource
    static class Rules {
        @ComponentType
        void register(ComponentTypeBuilder<PlayApplicationSpec> builder) {
            builder.defaultImplementation(DefaultPlayApplicationSpec.class);
        }

        @BinaryType
        void registerApplication(BinaryTypeBuilder<PlayApplicationBinarySpec> builder) {
            builder.defaultImplementation(DefaultPlayApplicationBinarySpec.class);
        }

        @ComponentBinaries
        void createBinaries(CollectionBuilder<PlayApplicationBinarySpec> binaries, final PlayApplicationSpec componentSpec, @Path("buildDir") final File buildDir){
            binaries.create(String.format("%sBinary", componentSpec.getName()), new Action<PlayApplicationBinarySpec>(){
                public void execute(PlayApplicationBinarySpec playBinary) {
                    PlayApplicationBinarySpecInternal playBinaryInternal = (PlayApplicationBinarySpecInternal) playBinary;
                    JavaVersion currentJava = JavaVersion.current();
                    playBinaryInternal.setTargetPlatform(new DefaultJavaPlatform(currentJava));
                    playBinaryInternal.setToolChain(new DefaultPlayToolChain(DEFAULT_PLAY_VERSION, DEFAULT_SCALA_VERSION, currentJava));
                    playBinaryInternal.setJarFile(new File(buildDir, String.format("jars/%s/%s.jar", componentSpec.getName(), playBinaryInternal.getName())));

                }
            });
        }

        @BinaryTasks
        void createRenderingTasks(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpec binary) {
            tasks.create(String.format("create%sJar", StringUtils.capitalize(binary.getName())), Jar.class, new Action<Jar>(){
                public void execute(Jar jar) {
                    jar.setDestinationDir(binary.getJarFile().getParentFile());
                    jar.setArchiveName(binary.getJarFile().getName());
                }
            });
        }
    }
}
