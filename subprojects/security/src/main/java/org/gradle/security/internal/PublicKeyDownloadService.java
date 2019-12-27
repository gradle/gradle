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
package org.gradle.security.internal;

import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.io.ExponentialBackoff;
import org.gradle.internal.resource.transfer.ExternalResourceAccessor;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.gradle.security.internal.SecuritySupport.toHexString;

public class PublicKeyDownloadService implements PublicKeyService {
    private final static Logger LOGGER = Logging.getLogger(PublicKeyDownloadService.class);

    private final List<URI> keyServers;
    private final ExternalResourceAccessor client;

    public PublicKeyDownloadService(List<URI> keyServers, ExternalResourceAccessor client) {
        this.keyServers = keyServers;
        this.client = client;
    }

    @Override
    public Optional<PGPPublicKey> findPublicKey(long id) {
        Optional<PGPPublicKeyRing> keyRing = findKeyRing(id);
        if (keyRing.isPresent()) {
            return findMatchingKey(id, keyRing.get());
        }
        return Optional.empty();
    }

    @Override
    public Optional<PGPPublicKeyRing> findKeyRing(long id) {
        List<URI> servers = new ArrayList<>(keyServers);
        Collections.shuffle(servers);
        return tryDownloadKeyFromServer(id, servers);
    }

    @SuppressWarnings("OptionalAssignedToNull")
    private Optional<PGPPublicKeyRing> tryDownloadKeyFromServer(long id, List<URI> baseUris) {
        Deque<URI> serversLeft = new ArrayDeque<>(baseUris);
        try {
            ExponentialBackoff<ExponentialBackoff.Signal> backoff = ExponentialBackoff.of(5, TimeUnit.SECONDS, 50, TimeUnit.MILLISECONDS);
            Optional<PGPPublicKeyRing> key = backoff.retryUntil(() -> {
                URI baseUri = serversLeft.poll();
                if (baseUri == null) {
                    // no more servers left despite retries
                    return Optional.empty();
                }
                try {
                    URI query = toQuery(baseUri, id);
                    ExternalResourceReadResponse response = client.openResource(query, false);
                    if (response != null) {
                        return Optional.of(findKeyRing(response));
                    } else {
                        logKeyDownloadAttempt(id, baseUri);
                        // null means the resource is missing from this repo
                    }
                } catch (Exception e) {
                    logKeyDownloadAttempt(id, baseUri);
                    // add for retry
                    serversLeft.add(baseUri);
                }
                // retry
                return null;
            });
            return key == null ? Optional.empty() : key;
        } catch (InterruptedException | IOException e) {
            return Optional.empty();
        }
    }

    /**
     * A response was sent from the server. This is a keyring, we need to find
     * within the keyring the matching key.
     */
    private Optional<PGPPublicKey> findMatchingKey(long id, PGPPublicKeyRing keyRing) {
        for (PGPPublicKey publicKey : keyRing) {
            if (publicKey.getKeyID() == id) {
                return Optional.of(publicKey);
            }
        }
        return Optional.empty();
    }

    private PGPPublicKeyRing findKeyRing(ExternalResourceReadResponse response) throws IOException {

        try (InputStream stream = response.openStream();
             InputStream decoderStream = PGPUtil.getDecoderStream(stream)) {
            PGPObjectFactory objectFactory = new PGPObjectFactory(
                decoderStream, new BcKeyFingerprintCalculator());
            return (PGPPublicKeyRing) objectFactory.nextObject();
        }
    }

    private static void logKeyDownloadAttempt(long id, URI baseUri) {
        LOGGER.debug("Cannot download public key " + toHexString(id) + " from " + baseUri.getHost());
    }

    private URI toQuery(URI baseUri, long key) throws URISyntaxException {
        String scheme = baseUri.getScheme();
        int port = baseUri.getPort();
        if ("hkp".equals(scheme)) {
            scheme = "http";
            port = 11371;
        }
        String keyId = toHexString(key);
        return new URI(scheme, null, baseUri.getHost(), port, "/pks/lookup", "op=get&options=mr&search=0x" + keyId, null);
    }

    @Override
    public void close() {
    }
}
