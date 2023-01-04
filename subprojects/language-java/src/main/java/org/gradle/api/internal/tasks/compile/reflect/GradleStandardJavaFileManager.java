/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.reflect;

import com.google.common.collect.Iterables;
import org.gradle.internal.classpath.ClassPath;

import javax.annotation.Nullable;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Set;

import static org.gradle.api.internal.tasks.compile.filter.AnnotationProcessorFilter.getFilteredClassLoader;

public class GradleStandardJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
    private final ClassPath annotationProcessorPath;
    private final boolean hasEmptySourcePaths;
    private final boolean hasPreviousClassOutput;

    private GradleStandardJavaFileManager(StandardJavaFileManager fileManager, ClassPath annotationProcessorPath, boolean hasEmptySourcePaths, @Nullable File previousClassOutput) {
        super(fileManager);
        this.annotationProcessorPath = annotationProcessorPath;
        this.hasEmptySourcePaths = hasEmptySourcePaths;
        this.hasPreviousClassOutput = previousClassOutput != null;
        registerPreviousClassOutput(previousClassOutput);
    }

    private void registerPreviousClassOutput(@Nullable File previousClassOutput) {
        if (previousClassOutput != null) {
            try {
                fileManager.setLocation(GradleLocation.PREVIOUS_CLASS_OUTPUT, Collections.singleton(previousClassOutput));
            } catch (IOException e) {
                throw new UncheckedIOException("Problem registering previous class output location", e);
            }
        }
    }

    /**
     * Overrides particular methods to prevent javac from accessing source files outside of Gradle's understanding or
     * classloaders outside of Gradle's control.
     */
    public static JavaFileManager wrap(StandardJavaFileManager delegate, ClassPath annotationProcessorPath, boolean hasEmptySourcePaths, @Nullable File previousClassOutput) {
        return new GradleStandardJavaFileManager(delegate, annotationProcessorPath, hasEmptySourcePaths, previousClassOutput);
    }

    @Override
    public boolean hasLocation(Location location) {
        if (hasEmptySourcePaths) {
            // There is currently a requirement in the JDK9 javac implementation
            // that when javac is invoked with an explicitly empty sourcepath
            // (i.e. {@code --sourcepath ""}), it won't allow you to compile a java 9
            // module. However, we really want to explicitly set an empty sourcepath
            // so that we don't implicitly pull in unrequested sourcefiles which
            // haven't been snapshotted because we will consider the task up-to-date
            // if the implicit files change.
            //
            // This implementation of hasLocation() pretends that the JavaFileManager
            // has no concept of a source path.
            if (location.equals(StandardLocation.SOURCE_PATH)) {
                return false;
            }
        }
        return super.hasLocation(location);
    }

    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
        if (hasEmptySourcePaths) {
            // If we are pretending that we don't have a sourcepath, the compiler will
            // look on the classpath for sources. Since we don't want to bring in any
            // sources implicitly from the classpath, we have to ignore source files
            // found on the classpath.
            if (location.equals(StandardLocation.CLASS_PATH)) {
                kinds.remove(JavaFileObject.Kind.SOURCE);
            }
        }

        if (hasPreviousClassOutput && location.equals(StandardLocation.CLASS_OUTPUT)) {
            // For Java module compilation we list also previous class output as class output.
            // This is needed for incremental compilation after a failure where we change output folders.
            // With that we make sure that all module classes/packages are found by javac.
            // In case one of --module-source-path or --source-path is provided, this makes sure, that javac won't automatically recompile
            // classes that are not in CLASS_OUTPUT. And in case when --module-source-path or --source-path are not provided,
            // this makes sure that javac doesn't fail on missing packages or classes that are not in CLASS_OUTPUT.
            // Second and last part of fix for: https://github.com/gradle/gradle/issues/23067
            Iterable<JavaFileObject> previousClassOutput = super.list(GradleLocation.PREVIOUS_CLASS_OUTPUT, packageName, kinds, recurse);
            Iterable<JavaFileObject> classOutput = super.list(location, packageName, kinds, recurse);
            return Iterables.concat(previousClassOutput, classOutput);
        }

        return super.list(location, packageName, kinds, recurse);
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        ClassLoader classLoader = super.getClassLoader(location);
        if (location.equals(StandardLocation.ANNOTATION_PROCESSOR_PATH)) {
            if (classLoader instanceof URLClassLoader) {
                return new URLClassLoader(annotationProcessorPath.getAsURLArray(), getFilteredClassLoader(classLoader.getParent()));
            }
        }

        return classLoader;
    }

    private enum GradleLocation implements Location {
        PREVIOUS_CLASS_OUTPUT;

        @Override
        public String getName() {
            return name();
        }

        @Override
        public boolean isOutputLocation() {
            return false;
        }
    }
}
