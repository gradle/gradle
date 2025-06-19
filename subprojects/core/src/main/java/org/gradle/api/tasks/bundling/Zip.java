/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.tasks.bundling;

import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.archive.ZipCopyAction;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.DefaultZipCompressor;
import org.gradle.api.internal.file.copy.ZipCompressor;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.work.DisableCachingByDefault;

import java.nio.charset.Charset;

/**
 * Assembles a ZIP archive.
 *
 * The default is to compress the contents of the zip.
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class Zip extends AbstractArchiveTask {
    public static final String ZIP_EXTENSION = "zip";

    public Zip() {
        getArchiveExtension().set(ZIP_EXTENSION);
        getEntryCompression().convention(ZipEntryCompression.DEFLATED);
        getZip64().convention(false);
    }

    @Internal
    protected ZipCompressor getCompressor() {
        switch (getEntryCompression().get()) {
            case DEFLATED:
                return new DefaultZipCompressor(getZip64().get(), ZipArchiveOutputStream.DEFLATED);
            case STORED:
                return new DefaultZipCompressor(getZip64().get(), ZipArchiveOutputStream.STORED);
            default:
                throw new IllegalArgumentException(String.format("Unknown Compression type %s", getEntryCompression().get()));
        }
    }

    @Override
    protected CopyAction createCopyAction() {
        validate();
        DocumentationRegistry documentationRegistry = getServices().get(DocumentationRegistry.class);
        return new ZipCopyAction(getArchiveFile().get().getAsFile(), getCompressor(), documentationRegistry, getMetadataCharset().getOrNull(), getPreserveFileTimestamps().get());
    }

    private void validate() {
        String metadataCharset = getMetadataCharset().getOrNull();
        if (metadataCharset != null && !Charset.isSupported(metadataCharset)) {
            throw new InvalidUserDataException(String.format("Charset for metadataCharset '%s' is not supported by your JVM", metadataCharset));
        }
    }

    /**
     * Returns the compression level of the entries of the archive. If set to {@link ZipEntryCompression#DEFLATED} (the default), each entry is
     * compressed using the DEFLATE algorithm. If set to {@link ZipEntryCompression#STORED} the entries of the archive are left uncompressed.
     *
     * @return the compression level of the archive contents.
     */
    @Input
    @ReplacesEagerProperty
    public abstract Property<ZipEntryCompression> getEntryCompression();

    /**
     * Whether the zip can contain more than 65535 files and/or support files greater than 4GB in size.
     * <p>
     * The standard zip format has hard limits on file size and count.
     * The <a href="http://en.wikipedia.org/wiki/Zip_(file_format)#ZIP64">Zip64 format extension</a>
     * practically removes these limits and is therefore required for building large zips.
     * <p>
     * However, not all Zip readers support the Zip64 extensions.
     * Notably, the {@link java.util.zip.ZipInputStream} JDK class does not support Zip64 for versions earlier than Java 7.
     * This means you should not enable this property if you are building JARs to be used with Java 6 and earlier runtimes.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getZip64();

    /**
     * Added for Kotlin source compatibility. Use {@link #getZip64()} instead.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsZip64() {
        ProviderApiDeprecationLogger.logDeprecation(Zip.class, "getIsZip64()", "getZip64()");
        return getZip64();
    }

    /**
     * The character set used to encode ZIP metadata like file names.
     * Defaults to the platform's default character set.
     *
     * @since 2.14
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getMetadataCharset();
}
