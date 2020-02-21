/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.resources;

import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.resources.TextResource;
import org.gradle.api.resources.TextResourceFactory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.verifier.HttpRedirectVerifier;
import org.gradle.internal.verifier.HttpRedirectVerifierFactory;
import org.gradle.util.GUtil;

import java.net.URI;
import java.nio.charset.Charset;

public class DefaultTextResourceFactory implements TextResourceFactory {
    private final FileOperations fileOperations;
    private final TemporaryFileProvider tempFileProvider;
    private ApiTextResourceAdapter.Factory apiTextResourcesAdapterFactory;

    public DefaultTextResourceFactory(FileOperations fileOperations, TemporaryFileProvider tempFileProvider, ApiTextResourceAdapter.Factory apiTextResourcesAdapterFactory) {
        this.fileOperations = fileOperations;
        this.tempFileProvider = tempFileProvider;
        this.apiTextResourcesAdapterFactory = apiTextResourcesAdapterFactory;
    }

    @Override
    public TextResource fromString(String string) {
        return new StringBackedTextResource(tempFileProvider, string);
    }

    @Override
    public TextResource fromFile(Object file, String charset) {
        return new FileCollectionBackedTextResource(tempFileProvider, fileOperations.immutableFiles(file), Charset.forName(charset));
    }

    @Override
    public TextResource fromFile(Object file) {
        return fromFile(file, Charset.defaultCharset().name());
    }

    @Override
    public TextResource fromArchiveEntry(Object archive, String entryPath, String charset) {
        return new FileCollectionBackedArchiveTextResource(fileOperations, tempFileProvider, fileOperations.immutableFiles(archive), entryPath, Charset.forName(charset));
    }

    @Override
    public TextResource fromArchiveEntry(Object archive, String entryPath) {
        return fromArchiveEntry(archive, entryPath, Charset.defaultCharset().name());
    }

    @Override
    public TextResource fromUri(Object uri) {
        return fromUri(uri, false);
    }

    @Override
    public TextResource fromInsecureUri(Object uri) {
        return fromUri(uri, true);
    }

    private TextResource fromUri(Object uri, boolean allowInsecureProtocol) {
        URI rootUri = fileOperations.uri(uri);

        HttpRedirectVerifier redirectVerifier =
            HttpRedirectVerifierFactory.create(
                rootUri,
                allowInsecureProtocol,
                () -> DeprecationLogger
                    .deprecate("Loading a TextResource from an insecure URI")
                    .withAdvice(String.format("Switch the URI to '%s' or try 'resources.text.fromInsecureUri(\"%s\")' to silence the warning.", GUtil.toSecureUrl(rootUri), rootUri))
                    .withContext(String.format("The provided URI '%s' uses an insecure protocol (HTTP).", rootUri))
                    .willBeRemovedInGradle7()
                    .withDslReference(TextResourceFactory.class, "fromInsecureUri(java.lang.Object)")
                    .nagUser(),
                redirect -> DeprecationLogger
                    .deprecate("Loading a TextResource from an insecure redirect")
                    .withAdvice("Switch to HTTPS or use TextResourceFactory.fromInsecureUri(Object) to silence the warning.")
                    .withContext(String.format("'%s' redirects to insecure '%s'.", uri, redirect))
                    .willBeRemovedInGradle7()
                    .withDslReference(TextResourceFactory.class, "fromInsecureUri(java.lang.Object)")
                    .nagUser()
            );
        return apiTextResourcesAdapterFactory.create(rootUri, redirectVerifier);
    }
}
