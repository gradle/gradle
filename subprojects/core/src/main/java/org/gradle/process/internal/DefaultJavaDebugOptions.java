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

package org.gradle.process.internal;

import org.gradle.api.internal.model.InstantiatorBackedObjectFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.process.JavaDebugOptions;

import javax.inject.Inject;
import java.util.Objects;

public class DefaultJavaDebugOptions implements JavaDebugOptions {
    private final Property<Boolean> enabled;
    private final Property<String> host;
    private final Property<Integer> port;
    private final Property<Boolean> server;
    private final Property<Boolean> suspend;

    @Inject
    public DefaultJavaDebugOptions(ObjectFactory objectFactory) {
        this.enabled = objectFactory.property(Boolean.class).convention(false);
        this.host = objectFactory.property(String.class);
        this.port = objectFactory.property(Integer.class).convention(5005);
        this.server = objectFactory.property(Boolean.class).convention(true);
        this.suspend = objectFactory.property(Boolean.class).convention(true);
    }

    public DefaultJavaDebugOptions() {
        // Ugly, but there are a few places where we need to instantiate a JavaDebugOptions and a regular ObjectFactory service
        // is not available.
        this(new InstantiatorBackedObjectFactory(DirectInstantiator.INSTANCE));
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEnabled().get(), getHost().getOrNull(), getPort().get(), getServer().get(), getSuspend().get());
    }

    @SuppressWarnings("BoxedPrimitiveEquality")
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultJavaDebugOptions that = (DefaultJavaDebugOptions) o;
        return enabled.get() == that.enabled.get()
            && Objects.equals(host.getOrNull(), that.host.getOrNull())
            && port.get().equals(that.port.get())
            && server.get() == that.server.get()
            && suspend.get() == that.suspend.get();
    }

    @Override
    public Property<Boolean> getEnabled() {
        return enabled;
    }

    @Override
    @Optional
    public Property<String> getHost() {
        return host;
    }

    @Override
    public Property<Integer> getPort() {
        return port;
    }

    @Override
    public Property<Boolean> getServer() {
        return server;
    }

    @Override
    public Property<Boolean> getSuspend() {
        return suspend;
    }
}
