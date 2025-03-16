/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.Action;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.file.FileContents;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.provider.sources.EnvironmentVariableValueSource;
import org.gradle.api.internal.provider.sources.EnvironmentVariablesPrefixedByValueSource;
import org.gradle.api.internal.provider.sources.FileBytesValueSource;
import org.gradle.api.internal.provider.sources.FileTextValueSource;
import org.gradle.api.internal.provider.sources.GradlePropertiesPrefixedByValueSource;
import org.gradle.api.internal.provider.sources.GradlePropertyValueSource;
import org.gradle.api.internal.provider.sources.SystemPropertiesPrefixedByValueSource;
import org.gradle.api.internal.provider.sources.SystemPropertyValueSource;
import org.gradle.api.internal.provider.sources.process.DefaultExecOutput;
import org.gradle.api.internal.provider.sources.process.ProcessOutputProviderFactory;
import org.gradle.api.internal.provider.sources.process.ProcessOutputValueSource;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.provider.ValueSourceSpec;
import org.gradle.internal.event.ListenerManager;
import org.gradle.process.ExecOutput;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

public class DefaultProviderFactory implements ProviderFactory {
    @Nullable
    private final ValueSourceProviderFactory valueSourceProviderFactory;
    @Nullable
    private final ProcessOutputProviderFactory processOutputProviderFactory;

    private final CredentialsProviderFactory credentialsProviderFactory;

    public DefaultProviderFactory() {
        this(null, null, null, null);
    }

    public DefaultProviderFactory(
        @Nullable ValueSourceProviderFactory valueSourceProviderFactory,
        @Nullable ProcessOutputProviderFactory processOutputProviderFactory,
        @Nullable ListenerManager listenerManager,
        @Nullable ObjectFactory objectFactory
    ) {
        this.valueSourceProviderFactory = valueSourceProviderFactory;
        this.processOutputProviderFactory = processOutputProviderFactory;
        this.credentialsProviderFactory = new CredentialsProviderFactory(this, objectFactory);
        if (listenerManager != null) {
            listenerManager.addListener(credentialsProviderFactory);
        }
    }

    @Override
    public <T> Provider<T> provider(final Callable<? extends T> value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        return new DefaultProvider<>(value);
    }

    @Override
    public Provider<String> environmentVariable(String variableName) {
        return environmentVariable(Providers.of(variableName));
    }

    @Override
    public Provider<String> environmentVariable(Provider<String> variableName) {
        return of(
            EnvironmentVariableValueSource.class,
            spec -> spec.getParameters().getVariableName().set(variableName)
        );
    }

    @Override
    public Provider<Map<String, String>> environmentVariablesPrefixedBy(String variableNamePrefix) {
        return environmentVariablesPrefixedBy(Providers.of(variableNamePrefix));
    }

    @Override
    public Provider<Map<String, String>> environmentVariablesPrefixedBy(Provider<String> variableNamePrefix) {
        return of(
            EnvironmentVariablesPrefixedByValueSource.class,
            spec -> spec.getParameters().getPrefix().set(variableNamePrefix)
        );
    }

    @Override
    public Provider<String> systemProperty(String propertyName) {
        return systemProperty(Providers.of(propertyName));
    }

    @Override
    public Provider<String> systemProperty(Provider<String> propertyName) {
        return of(
            SystemPropertyValueSource.class,
            spec -> spec.getParameters().getPropertyName().set(propertyName)
        );
    }

    @Override
    public Provider<Map<String, String>> systemPropertiesPrefixedBy(String variableNamePrefix) {
        return systemPropertiesPrefixedBy(Providers.of(variableNamePrefix));
    }

    @Override
    public Provider<Map<String, String>> systemPropertiesPrefixedBy(Provider<String> variableNamePrefix) {
        return of(
            SystemPropertiesPrefixedByValueSource.class,
            spec -> spec.getParameters().getPrefix().set(variableNamePrefix)
        );
    }

    @Override
    public Provider<String> gradleProperty(String propertyName) {
        return gradleProperty(Providers.of(propertyName));
    }

    @Override
    public Provider<String> gradleProperty(Provider<String> propertyName) {
        return of(
            GradlePropertyValueSource.class,
            spec -> spec.getParameters().getPropertyName().set(propertyName)
        );
    }

    @Override
    public Provider<Map<String, String>> gradlePropertiesPrefixedBy(String variableNamePrefix) {
        return gradlePropertiesPrefixedBy(Providers.of(variableNamePrefix));
    }

    @Override
    public Provider<Map<String, String>> gradlePropertiesPrefixedBy(Provider<String> variableNamePrefix) {
        return of(
            GradlePropertiesPrefixedByValueSource.class,
            spec -> spec.getParameters().getPrefix().set(variableNamePrefix)
        );
    }

    @Override
    public FileContents fileContents(RegularFile file) {
        return fileContents(property -> property.set(file));
    }

    @Override
    public FileContents fileContents(Provider<RegularFile> file) {
        return fileContents(property -> property.set(file));
    }

    private FileContents fileContents(Action<RegularFileProperty> setFileProperty) {
        return new FileContents() {
            @Override
            public Provider<String> getAsText() {
                return of(
                    FileTextValueSource.class,
                    spec -> setFileProperty.execute(spec.getParameters().getFile())
                );
            }

            @Override
            public Provider<byte[]> getAsBytes() {
                return of(
                    FileBytesValueSource.class,
                    spec -> setFileProperty.execute(spec.getParameters().getFile())
                );
            }
        };
    }

    @Override
    public ExecOutput exec(Action<? super ExecSpec> action) {
        if (processOutputProviderFactory == null) {
            throw new UnsupportedOperationException();
        }
        return new DefaultExecOutput(of(ProcessOutputValueSource.class, spec -> processOutputProviderFactory.configureParametersForExec(spec.getParameters(), action)));
    }

    @Override
    public ExecOutput javaexec(Action<? super JavaExecSpec> action) {
        if (processOutputProviderFactory == null) {
            throw new UnsupportedOperationException();
        }
        return new DefaultExecOutput(of(ProcessOutputValueSource.class, spec -> processOutputProviderFactory.configureParametersForJavaExec(spec.getParameters(), action)));
    }

    @Override
    public <T, P extends ValueSourceParameters> Provider<T> of(Class<? extends ValueSource<T, P>> valueSourceType, Action<? super ValueSourceSpec<P>> configuration) {
        if (valueSourceProviderFactory == null) {
            throw new UnsupportedOperationException();
        }
        return valueSourceProviderFactory.createProviderOf(valueSourceType, configuration);
    }

    @Override
    public <T extends Credentials> Provider<T> credentials(Class<T> credentialsType, String identity) {
        return credentialsProviderFactory.provide(credentialsType, identity);
    }

    @Override
    public <T extends Credentials> Provider<T> credentials(Class<T> credentialsType, Provider<String> identity) {
        return credentialsProviderFactory.provide(credentialsType, identity);
    }

    @Override
    public <A, B, R> Provider<R> zip(Provider<A> left, Provider<B> right, BiFunction<? super A, ? super B, ? extends R> combiner) {
        return left.zip(right, combiner);
    }

}
