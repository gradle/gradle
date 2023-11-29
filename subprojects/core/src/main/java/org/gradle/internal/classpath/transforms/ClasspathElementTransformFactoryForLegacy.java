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

package org.gradle.internal.classpath.transforms;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.api.internal.file.archive.ZipInput;
import org.gradle.api.internal.file.archive.impl.FileZipInput;
import org.gradle.internal.classpath.ClasspathBuilder;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.classpath.types.InstrumentingTypeRegistry;
import org.gradle.internal.file.FileException;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.internal.GFileUtils;
import org.gradle.util.internal.JarUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

@ServiceScope(Scopes.UserHome.class)
public class ClasspathElementTransformFactoryForLegacy implements ClasspathElementTransformFactory {

    private final ClasspathBuilder classpathBuilder;
    private final ClasspathWalker classpathWalker;

    public ClasspathElementTransformFactoryForLegacy(ClasspathBuilder classpathBuilder, ClasspathWalker classpathWalker) {
        this.classpathBuilder = classpathBuilder;
        this.classpathWalker = classpathWalker;
    }

    @Override
    public void applyConfigurationTo(Hasher hasher) {
        // Do nothing, this is compatible with the old instrumentation
    }

    @Override
    public ClasspathElementTransform createTransformer(File source, ClassTransform classTransform, InstrumentingTypeRegistry typeRegistry) {
        Boolean isMultiReleaseJar = null;

        if (source.isFile()) {
            // Walk a file to figure out if it is signed and if it is a multi-release JAR.
            try (ZipInput entries = FileZipInput.create(source)) {
                for (ZipEntry entry : entries) {
                    String entryName = entry.getName();
                    if (isJarSignatureFile(entryName)) {
                        // TODO(mlopatkin) Manifest of the signed JAR contains signature information and must be the first entry in the JAR.
                        //  Looking into the manifest here should be more effective.
                        // This policy doesn't transform signed JARs so no further checks are necessary.
                        return new SkipClasspathElementTransform(source);
                    }
                    if (isMultiReleaseJar == null && JarUtil.isManifestName(entryName)) {
                        isMultiReleaseJar = JarUtil.isMultiReleaseJarManifest(JarUtil.readManifest(entry.getContent()));
                    }
                }
            } catch (FileException e) {
                // Ignore malformed archive, let the transformation handle it.
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        if (isMultiReleaseJar != null && isMultiReleaseJar) {
            return new MultiReleaseClasspathElementTransformForLegacy(source, classpathBuilder, classpathWalker, typeRegistry, classTransform);
        }
        return new BaseClasspathElementTransform(source, classpathBuilder, classpathWalker, typeRegistry, classTransform);
    }

    private boolean isJarSignatureFile(String entryName) {
        return entryName.startsWith("META-INF/") && entryName.endsWith(".SF");
    }

    @Override
    public String toString() {
        return "TransformFactory(legacy)";
    }

    /**
     * A no-op transformation that copies the original file verbatim. Can be used if the original cannot be instrumented under policy.
     */
    private static class SkipClasspathElementTransform implements ClasspathElementTransform {

        private static final Logger LOGGER = LoggerFactory.getLogger(SkipClasspathElementTransform.class);

        private final File source;

        public SkipClasspathElementTransform(File source) {
            this.source = source;
        }

        @Override
        public void transform(File destination) {
            LOGGER.debug("Archive '{}' rejected by policy. Skipping instrumentation.", source.getName());
            GFileUtils.copyFile(source, destination);
        }
    }
}
