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

package org.gradle.api.tasks;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.internal.IoActions;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.util.PropertiesUtils;
import org.gradle.util.internal.DeferredUtil;
import org.jspecify.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

/**
 * Writes a {@link java.util.Properties} in a way that the results can be expected to be reproducible.
 *
 * <p>There are a number of differences compared to how properties are stored:</p>
 * <ul>
 *     <li>no timestamp comment is generated at the beginning of the file</li>
 *     <li>the lines in the resulting files are separated by a pre-set separator (defaults to
 *         {@literal '\n'}) instead of the system default line separator</li>
 *     <li>the properties are sorted alphabetically</li>
 * </ul>
 *
 * <p>Like with {@link java.util.Properties}, Unicode characters are escaped when using the
 * default Latin-1 (ISO-8559-1) encoding.</p>
 *
 * @see java.util.Properties#store(OutputStream, String)
 * @since 3.3
 */
@CacheableTask
public abstract class WriteProperties extends DefaultTask {
    private final Map<String, Callable<String>> deferredProperties = new HashMap<>();
    private final Map<String, String> properties = new HashMap<>();
    private String lineSeparator = "\n";
    private String comment;
    private String encoding = "ISO_8859_1";

    /**
     * Returns an immutable view of properties to be written to the properties file.
     *
     * @since 3.3
     */
    @Input
    @ToBeReplacedByLazyProperty
    public Map<String, String> getProperties() {
        ImmutableMap.Builder<String, String> propertiesBuilder = ImmutableMap.builder();
        propertiesBuilder.putAll(properties);
        try {
            for (Map.Entry<String, Callable<String>> e : deferredProperties.entrySet()) {
                propertiesBuilder.put(e.getKey(), e.getValue().call());
            }
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        return propertiesBuilder.build();
    }

    /**
     * Sets all properties to be written to the properties file replacing any existing properties.
     *
     * @see #properties(Map)
     * @see #property(String, Object)
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties.clear();
        properties(properties);
    }

    /**
     * Adds a property to be written to the properties file.
     * <p>
     * A property's value will be coerced to a <code>String</code> with <code>String#valueOf(Object)</code> or a
     * {@link Callable} returning a value to be coerced into a <code>String</code>.
     * </p>
     * <p>
     * Values are not allowed to be null.
     * </p>
     *
     * @param name Name of the property
     * @param value Value of the property
     * @since 3.4
     */
    public void property(final String name, final Object value) {
        checkForNullValue(name, value);
        if (DeferredUtil.isDeferred(value)) {
            deferredProperties.put(name, new Callable<String>() {
                @Override
                public String call() throws Exception {
                    Object futureValue = DeferredUtil.unpack(value);
                    checkForNullValue(name, futureValue);
                    return String.valueOf(futureValue);
                }
            });
        } else {
            properties.put(name, String.valueOf(value));
        }
    }

    /**
     * Adds multiple properties to be written to the properties file.
     * <p>
     * This is a convenience method for calling {@link #property(String, Object)} multiple times.
     * </p>
     *
     * @param properties Properties to be added
     * @see #property(String, Object)
     * @since 3.4
     */
    public void properties(Map<String, Object> properties) {
        for (Map.Entry<String, Object> e : properties.entrySet()) {
            property(e.getKey(), e.getValue());
        }
    }

    /**
     * Returns the line separator to be used when creating the properties file.
     * Defaults to {@literal `\n`}.
     */
    @Input
    @ToBeReplacedByLazyProperty
    public String getLineSeparator() {
        return lineSeparator;
    }

    /**
     * Sets the line separator to be used when creating the properties file.
     */
    public void setLineSeparator(String lineSeparator) {
        this.lineSeparator = lineSeparator;
    }

    /**
     * Returns the optional comment to add at the beginning of the properties file.
     */
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    public String getComment() {
        return comment;
    }

    /**
     * Sets the optional comment to add at the beginning of the properties file.
     */
    public void setComment(@Nullable String comment) {
        this.comment = comment;
    }

    /**
     * Returns the encoding used to write the properties file. Defaults to {@literal ISO_8859_1}.
     * If set to anything different, unicode escaping is turned off.
     */
    @Input
    @ToBeReplacedByLazyProperty
    public String getEncoding() {
        return encoding;
    }

    /**
     * Sets the encoding used to write the properties file. Defaults to {@literal ISO_8859_1}.
     * If set to anything different, unicode escaping is turned off.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Returns the output file to write the properties to.
     */
    @Internal
    @Deprecated
    public File getOutputFile() {
        deprecationWarning();
        return getDestinationFile().getAsFile().getOrNull();
    }

    private void deprecationWarning() {
        DeprecationLogger.deprecateProperty(WriteProperties.class, "outputFile").replaceWith("destinationFile")
            .willBeRemovedInGradle9()
            .withDslReference()
            .nagUser();
    }

    /**
     * Sets the output file to write the properties to.
     *
     * @deprecated Use {@link #getDestinationFile()} instead.
     *
     * @since 4.0
     */
    @Deprecated
    public void setOutputFile(File outputFile) {
        deprecationWarning();
        getDestinationFile().set(outputFile);
    }

    /**
     * Sets the output file to write the properties to.
     *
     * @deprecated Use {@link #getDestinationFile()} instead.
     */
    @Deprecated
    public void setOutputFile(Object outputFile) {
        deprecationWarning();
        getDestinationFile().set(getServices().get(FileOperations.class).file(outputFile));
    }

    /**
     * The output properties file.
     *
     * @since 8.1
     */
    @OutputFile
    abstract public RegularFileProperty getDestinationFile();

    @TaskAction
    public void writeProperties() throws IOException {
        Charset charset = Charset.forName(getEncoding());
        File file = getDestinationFile().getAsFile().get();
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        try {
            Properties propertiesToWrite = new Properties();
            propertiesToWrite.putAll(getProperties());
            PropertiesUtils.store(propertiesToWrite, out, getComment(), charset, getLineSeparator());
        } finally {
            IoActions.closeQuietly(out);
        }
    }

    private static void checkForNullValue(String key, Object value) {
        Preconditions.checkNotNull(value, "Property '%s' is not allowed to have a null value.", key);
    }
}
