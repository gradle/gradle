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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.internal.classloader.TransformReplacer.MarkerResource;
import org.gradle.internal.classpath.ClasspathBuilder;
import org.gradle.internal.classpath.ClasspathEntryVisitor;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.classpath.types.InstrumentingTypeRegistry;
import org.gradle.util.internal.JarUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.gradle.internal.classpath.InstrumentingClasspathFileTransformer.isSupportedVersion;

/**
 * Transformation for agent-based instrumentation.
 */
public class TransformationForAgent extends BaseTransformation {

    private static final Logger LOGGER = Logging.getLogger(TransformationForAgent.class);

    private int lowestUnsupportedVersionInJar = Integer.MAX_VALUE;
    private boolean isMultiReleaseJar;

    public TransformationForAgent(File source, ClasspathBuilder classpathBuilder, ClasspathWalker classpathWalker, InstrumentingTypeRegistry typeRegistry, ClassTransform transform) {
        super(source, classpathBuilder, classpathWalker, typeRegistry, transform);
    }

    @Override
    protected void processClassFile(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry classEntry) throws IOException {
        // We can filter out "unsupported" classes without checking the manifest beforehand.
        // Even if this JAR isn't multi-release per manifest, classes in META-INF/ cannot be loaded, so they are just weird resources.
        // The agent-based instrumentation doesn't load resources from the instrumented JAR, but from the original.
        // TODO(https://github.com/gradle/gradle/issues/18024) we really shouldn't instrument these "resource-looks-like-class" things.

        // We don't know the actual minimal supported version of the non-versioned class entries.
        // We fall back to some supported default to make checks below simpler.
        int version = JarUtil.getVersionedDirectoryMajorVersion(classEntry.getName()).orElse(AsmConstants.MIN_SUPPORTED_JAVA_VERSION);
        if (isSupportedVersion(version)) {
            super.processClassFile(builder, classEntry);
        } else if (lowestUnsupportedVersionInJar > version) {
            lowestUnsupportedVersionInJar = version;
        }
    }

    @Override
    protected void processManifest(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry manifestEntry) throws IOException {
        try {
            Manifest parsedManifest = JarUtil.readManifest(manifestEntry.getContent());
            if (!JarUtil.isMultiReleaseJarManifest(parsedManifest)) {
                // If the original JAR is not multi-release, we don't need the manifest in the transformed JAR at all.
                return;
            }
            isMultiReleaseJar = true;

            // We want the transformed JAR to also be a proper multi-release JAR.
            // To do so it must have the "Multi-Release: true" attribute.
            // "Manifest-Version" attribute is also required.
            // For everything else (classpath, sealed, etc.) classloader will check the original JAR, so no need to copy it.
            Manifest processedManifest = new Manifest();
            copyManifestMainAttribute(parsedManifest, processedManifest, Attributes.Name.MANIFEST_VERSION);
            setManifestMainAttribute(processedManifest, JarUtil.MULTI_RELEASE_ATTRIBUTE, "true");

            builder.put(manifestEntry.getName(), toByteArray(processedManifest), manifestEntry.getCompressionMethod());
        } catch (IOException e) {
            LOGGER.debug("Failed to parse Manifest from JAR " + source);
            throw e;
        }
    }

    @Override
    protected void processResource(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry resourceEntry) {
        // Class loader loads resources from the original JAR, so there's no need to put them into the transformed JAR.
        // Only classes affect the class-loading
    }

    @Override
    protected void finishProcessing(ClasspathBuilder.EntryBuilder builder) throws IOException {
        if (isMultiReleaseJar) {
            // Put marker resource into a multi-release JAR so the classloader can recognize that it tries to load non-instrumented classes.
            // Root directory is always supported. Every Java version before lowestUnsupportedVersion should also load from root.
            builder.put(MarkerResource.RESOURCE_NAME, MarkerResource.TRANSFORMED.asBytes());
            if (hasUnsupportedVersionInJar()) {
                // Every Java version starting from lowestUnsupportedVersion should see this jar as unsupported.
                builder.put(JarUtil.toVersionedPath(lowestUnsupportedVersionInJar, MarkerResource.RESOURCE_NAME), MarkerResource.NOT_TRANSFORMED.asBytes());
            }
        }
    }

    private boolean hasUnsupportedVersionInJar() {
        return lowestUnsupportedVersionInJar < Integer.MAX_VALUE;
    }

    private void copyManifestMainAttribute(Manifest source, Manifest destination, Attributes.Name name) {
        destination.getMainAttributes().put(name, source.getMainAttributes().getValue(name));
    }

    private void setManifestMainAttribute(Manifest manifest, String name, String value) {
        manifest.getMainAttributes().putValue(name, value);
    }

    private byte[] toByteArray(Manifest manifest) throws IOException {
        ByteArrayOutputStream manifestOutput = new ByteArrayOutputStream(512);
        manifest.write(manifestOutput);
        return manifestOutput.toByteArray();
    }
}
