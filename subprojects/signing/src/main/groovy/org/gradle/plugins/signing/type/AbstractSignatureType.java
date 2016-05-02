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
package org.gradle.plugins.signing.type;

import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.plugins.signing.signatory.Signatory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.codehaus.groovy.runtime.ResourceGroovyMethods.newInputStream;
import static org.codehaus.groovy.runtime.ResourceGroovyMethods.newOutputStream;
import static org.gradle.internal.IoActions.withResource;

/**
 * Convenience base class for {@link SignatureType} implementations.
 */
public abstract class AbstractSignatureType implements SignatureType {

    @Override
    public File sign(final Signatory signatory, File toSign) {
        final File signatureFile = fileFor(toSign);
        try {
            withResource(newInputStream(toSign), new Action<InputStream>() {
                @Override
                public void execute(final InputStream toSignStream) {
                    try {
                        withResource(newOutputStream(signatureFile), new Action<BufferedOutputStream>() {
                            @Override
                            public void execute(BufferedOutputStream signatureFileStream) {
                                sign(signatory, toSignStream, signatureFileStream);
                            }
                        });
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
        return signatureFile;
    }

    @Override
    public void sign(Signatory signatory, InputStream toSign, OutputStream destination) {
        signatory.sign(toSign, destination);
    }

    @Override
    public File fileFor(File toSign) {
        return new File(toSign.getPath() + "." + getExtension());
    }

    @Override
    public String combinedExtension(File toSign) {
        String name = toSign.getName();
        int dotIndex = name.lastIndexOf(".");
        if (dotIndex == -1 || dotIndex + 1 == name.length()) {
            return getExtension();
        } else {
            return name.substring(dotIndex + 1) + "." + getExtension();
        }
    }
}
