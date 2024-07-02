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
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.IoActions;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.util.PropertiesUtils;
import org.gradle.util.internal.DeferredUtil;

import javax.inject.Inject;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

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

    public WriteProperties() {
        getEncoding().convention("ISO_8859_1");
        getLineSeparator().convention("\n");
    }

    /**
     * Returns an immutable view of properties to be written to the properties file.
     */
    @Input
    @ReplacesEagerProperty(adapter = PropertiesAdapter.class)
    public abstract MapProperty<String, Object> getProperties();

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
     * @param value Value of the property or provider for value
     * @since 3.4
     */
    public void property(final String name, final Object value) {
        checkForNullValue(name, value);
        if (DeferredUtil.isDeferred(value)) {
            if (value instanceof Provider) {
                getProperties().put(name, (Provider<?>) value);
            } else {
                getProperties().put(name, getProviderFactory().provider(() -> {
                    Object futureValue = DeferredUtil.unpack(value);
                    checkForNullValue(name, futureValue);
                    return futureValue;
                }));
            }
        } else {
            getProperties().put(name, value);
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
    @Deprecated
    public void properties(Map<String, Object> properties) {
        ProviderApiDeprecationLogger.logDeprecation(WriteProperties.class, "properties(Map<String, Object>)", "properties");
        for (Map.Entry<String, Object> e : properties.entrySet()) {
            property(e.getKey(), e.getValue());
        }
    }

    /**
     * The line separator to be used when creating the properties file.
     * Defaults to {@literal `\n`}.
     */
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getLineSeparator();

    /**
     * The optional comment to add at the beginning of the properties file.
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    public abstract Property<String> getComment();

    /**
     * The encoding used to write the properties file. Defaults to {@literal ISO_8859_1}.
     * If set to anything different, unicode escaping is turned off.
     */
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getEncoding();

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
        Charset charset = Charset.forName(getEncoding().get());
        File file = getDestinationFile().getAsFile().get();
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        try {
            Properties propertiesToWrite = new Properties();
            for (Map.Entry<String, Object> entry : getProperties().get().entrySet()) {
                propertiesToWrite.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            PropertiesUtils.store(propertiesToWrite, out, getComment().getOrNull(), charset, getLineSeparator().get());
        } finally {
            IoActions.closeQuietly(out);
        }
    }

    @Inject
    public abstract ProviderFactory getProviderFactory();

    private static void checkForNullValue(String key, Object value) {
        Preconditions.checkNotNull(value, "Property '%s' is not allowed to have a null value.", key);
    }

    static class PropertiesAdapter {
        @BytecodeUpgrade
        static Map<String, String> getProperties(WriteProperties task) {
            return task
                .getProperties()
                .get().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
        }

        /**
         * Sets all properties to be written to the properties file replacing any existing properties.
         *
         * @see #properties(Map)
         * @see #property(String, Object)
         */
        @BytecodeUpgrade
        static void setProperties(WriteProperties task, Map<String, Object> properties) {
            task.getProperties().empty();
            task.properties(properties);
        }
    }
}
