/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.GradleConnection;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.internal.protocol.GradleConnectionFactoryVersion1;
import org.gradle.tooling.internal.protocol.GradleConnectionVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseBuildVersion1;
import org.gradle.tooling.model.Build;

import java.io.*;

public class GradleConnectionFactory {
    private String implementationClassName;
    private GradleConnectionFactoryVersion1 implementationFactory;
    private final ProtocolToModelAdapter adapter = new ProtocolToModelAdapter();
    public final ClassLoader classLoader;

    public GradleConnectionFactory() {
        this(null);
    }

    GradleConnectionFactory(String implementationClassName) {
        this.implementationClassName = implementationClassName;
        classLoader = getClass().getClassLoader();
    }

    public GradleConnectionFactoryVersion1 getImplementationFactory() {
        if (implementationFactory == null) {
            implementationClassName = loadImplementationClassName();
            implementationFactory = instantiateFactory();
        }
        return implementationFactory;
    }

    private GradleConnectionFactoryVersion1 instantiateFactory() {
        try {
            return (GradleConnectionFactoryVersion1) classLoader.loadClass(implementationClassName).newInstance();
        } catch (Throwable t) {
            throw new GradleConnectionException(String.format("Could not create instance of '%s'.", implementationClassName), t);
        }
    }

    private String loadImplementationClassName() {
        if (implementationClassName != null) {
            return implementationClassName;
        }

        try {
            InputStream inputStream = classLoader.getResourceAsStream("META-INF/services/org.gradle.tooling.internal.protocol.GradleConnectionFactoryVersion1");
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.replaceAll("#.*", "").trim();
                    if (!line.isEmpty()) {
                        return line;
                    }
                }
            } finally {
                inputStream.close();
            }
            throw new UnsupportedOperationException();
        } catch (IOException e) {
            throw new GradleConnectionException(String.format("Could not determine class name of GradleConnectionFactory implementation."), e);
        }
    }

    public GradleConnection create(File projectDir) {
        GradleConnectionFactoryVersion1 factory = getImplementationFactory();
        final GradleConnectionVersion1 gradleConnection = factory.create(projectDir);
        return new GradleConnection() {
            public <T extends Build> T getModel(Class<T> viewType) {
                return adapter.adapt(viewType, gradleConnection.getModel(EclipseBuildVersion1.class));
            }
        };
    }
}
