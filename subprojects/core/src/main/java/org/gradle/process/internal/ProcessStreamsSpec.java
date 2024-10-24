/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.io.InputStream;
import java.io.OutputStream;


public class ProcessStreamsSpec {

    private final Property<InputStream> standardInput;
    private final Property<OutputStream> standardOutput;
    private final Property<OutputStream> errorOutput;

    @Inject
    public ProcessStreamsSpec(ObjectFactory objectFactory) {
        this.standardInput = objectFactory.property(InputStream.class);
        this.standardOutput = objectFactory.property(OutputStream.class);
        this.errorOutput = objectFactory.property(OutputStream.class);
    }

    public Property<InputStream> getStandardInput() {
        return standardInput;
    }

    public Property<OutputStream> getStandardOutput() {
        return standardOutput;
    }

    public Property<OutputStream> getErrorOutput() {
        return errorOutput;
    }
}
