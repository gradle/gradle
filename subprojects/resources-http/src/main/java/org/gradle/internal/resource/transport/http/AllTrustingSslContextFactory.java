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

package org.gradle.internal.resource.transport.http;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.gradle.internal.UncheckedException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

public class AllTrustingSslContextFactory implements SslContextFactory {

    private static final Supplier<SSLContext> SSL_CONTEXT_SUPPLIER = Suppliers.memoize(new Supplier<SSLContext>() {
        @Override
        public SSLContext get() {
            try {
                SSLContext sslcontext = SSLContext.getInstance("TLS");
                sslcontext.init(null, ALL_TRUSTING_TRUST_MANAGER, null);
                return sslcontext;
            } catch (NoSuchAlgorithmException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            } catch (KeyManagementException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    });

    @Override
    public SSLContext createSslContext() {
        return SSL_CONTEXT_SUPPLIER.get();
    }

    private static final TrustManager[] ALL_TRUSTING_TRUST_MANAGER = new TrustManager[]{
        new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }
    };

}
