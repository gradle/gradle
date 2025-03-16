/*
 * Copyright 2007-2009 the original author or authors.
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

package org.gradle.wrapper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Download implements IDownload {
    public static final String UNKNOWN_VERSION = "0";
    public static final int DEFAULT_NETWORK_TIMEOUT_MILLISECONDS = 10 * 1000;

    private static final int BUFFER_SIZE = 10 * 1024;
    private static final int PROGRESS_CHUNK = 1024 * 1024;
    private final Logger logger;
    private final String appName;
    private final String appVersion;
    private final DownloadProgressListener progressListener;
    private final Map<String, String> systemProperties;
    private final int networkTimeout;

    public Download(Logger logger, String appName, String appVersion) {
        this(logger, null, appName, appVersion, convertSystemProperties(System.getProperties()));
    }

    public Download(Logger logger, String appName, String appVersion, int networkTimeout) {
        this(logger, null, appName, appVersion, convertSystemProperties(System.getProperties()), networkTimeout);
    }

    private static Map<String, String> convertSystemProperties(Properties properties) {
        Map<String, String> result = new HashMap<String, String>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue() == null ? null : entry.getValue().toString());
        }
        return result;
    }

    public Download(Logger logger, DownloadProgressListener progressListener, String appName, String appVersion, Map<String, String> systemProperties) {
        this(logger, progressListener, appName, appVersion, systemProperties, DEFAULT_NETWORK_TIMEOUT_MILLISECONDS);
    }

    public Download(Logger logger, DownloadProgressListener progressListener, String appName, String appVersion, Map<String, String> systemProperties, int networkTimeout) {
        this.logger = logger;
        this.appName = appName;
        this.appVersion = appVersion;
        this.systemProperties = systemProperties;
        this.progressListener = new DefaultDownloadProgressListener(logger, progressListener);
        this.networkTimeout = networkTimeout;
        configureProxyAuthentication();
    }

    private void configureProxyAuthentication() {
        if (systemProperties.get("http.proxyUser") != null || systemProperties.get("https.proxyUser") != null) {
            // Only an authenticator for proxies needs to be set. Basic authentication is supported by directly setting the request header field.
            Authenticator.setDefault(new ProxyAuthenticator(systemProperties));
        }
    }

    public void sendHeadRequest(URI uri) throws Exception {
        URL safeUrl = safeUri(uri).toURL();
        int responseCode = -1;
        try {
            HttpURLConnection conn = (HttpURLConnection)safeUrl.openConnection();
            conn.setRequestMethod("HEAD");
            addBasicAuthentication(uri, conn);
            conn.setRequestProperty("User-Agent", calculateUserAgent());
            conn.setConnectTimeout(networkTimeout);
            conn.connect();
            responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("HEAD request to " + safeUrl + " failed: response code (" + responseCode + ")");
            }
        } catch (IOException e) {
            throw new RuntimeException("HEAD request to " + safeUrl + " failed: response code (" + responseCode + "), timeout (" + networkTimeout + "ms)", e);
        }
    }

    @Override
    public void download(URI address, File destination) throws Exception {
        destination.getParentFile().mkdirs();
        downloadInternal(address, destination);
    }

    private void downloadInternal(URI address, File destination)
        throws Exception {
        OutputStream out = null;
        URLConnection conn;
        InputStream in = null;
        URL safeUrl = safeUri(address).toURL();
        try {
            out = new BufferedOutputStream(new FileOutputStream(destination));

            // No proxy is passed here as proxies are set globally using the HTTP(S) proxy system properties. The respective protocol handler implementation then makes use of these properties.
            conn = safeUrl.openConnection();

            addBasicAuthentication(address, conn);
            final String userAgentValue = calculateUserAgent();
            conn.setRequestProperty("User-Agent", userAgentValue);
            conn.setConnectTimeout(networkTimeout);
            conn.setReadTimeout(networkTimeout);
            in = conn.getInputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int numRead;
            int totalLength = conn.getContentLength();
            long downloadedLength = 0;
            long progressCounter = 0;
            while ((numRead = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Download was interrupted.");
                }

                downloadedLength += numRead;
                progressCounter += numRead;

                if (progressCounter / PROGRESS_CHUNK > 0 || downloadedLength == totalLength) {
                    progressCounter = progressCounter - PROGRESS_CHUNK;
                    progressListener.downloadStatusChanged(address, totalLength, downloadedLength);
                }

                out.write(buffer, 0, numRead);
            }
        } catch (SocketTimeoutException e) {
            throw new IOException("Downloading from " + safeUrl + " failed: timeout (" + networkTimeout + "ms)", e);
        } finally {
            logger.log("");
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }

    /**
     * Create a safe URI from the given one by stripping out user info.
     *
     * @param uri Original URI
     * @return a new URI with no user info
     */
    static URI safeUri(URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to parse URI", e);
        }
    }

    private void addBasicAuthentication(URI address, URLConnection connection) throws IOException {
        String userInfo = calculateUserInfo(address);
        if (userInfo == null) {
            return;
        }
        if (!"https".equals(address.getScheme())) {
            logger.log("WARNING Using HTTP Basic Authentication over an insecure connection to download the Gradle distribution. Please consider using HTTPS.");
        }
        connection.setRequestProperty("Authorization", "Basic " + base64Encode(userInfo));
    }

    /**
     * Base64 encode user info for HTTP Basic Authentication.
     *
     * Try to use {@literal java.util.Base64} encoder which is available starting with Java 8.
     * Fallback to {@literal javax.xml.bind.DatatypeConverter} from JAXB which is available starting with Java 6 but is not anymore in Java 9.
     * Fortunately, both of these two Base64 encoders implement the right Base64 flavor, the one that does not split the output in multiple lines.
     *
     * @param userInfo user info
     * @return Base64 encoded user info
     * @throws RuntimeException if no public Base64 encoder is available on this JVM
     */
    @SuppressWarnings("StringCharset")
    private String base64Encode(String userInfo) {
        ClassLoader loader = getClass().getClassLoader();
        try {
            Method getEncoderMethod = loader.loadClass("java.util.Base64").getMethod("getEncoder");
            Method encodeMethod = loader.loadClass("java.util.Base64$Encoder").getMethod("encodeToString", byte[].class);
            Object encoder = getEncoderMethod.invoke(null);
            return (String) encodeMethod.invoke(encoder, new Object[]{userInfo.getBytes("UTF-8")});
        } catch (Exception java7OrEarlier) {
            try {
                Method encodeMethod = loader.loadClass("javax.xml.bind.DatatypeConverter").getMethod("printBase64Binary", byte[].class);
                return (String) encodeMethod.invoke(null, new Object[]{userInfo.getBytes("UTF-8")});
            } catch (Exception java5OrEarlier) {
                throw new RuntimeException("Downloading Gradle distributions with HTTP Basic Authentication is not supported on your JVM.", java5OrEarlier);
            }
        }
    }

    private String calculateUserInfo(URI uri) {
        String username = systemProperties.get("gradle.wrapperUser");
        String password = systemProperties.get("gradle.wrapperPassword");
        if (username != null && password != null) {
            return username + ':' + password;
        }
        return uri.getUserInfo();
    }

    private String calculateUserAgent() {
        String javaVendor = systemProperties.get("java.vendor");
        String javaVersion = systemProperties.get("java.version");
        String javaVendorVersion = systemProperties.get("java.vm.version");
        String osName = systemProperties.get("os.name");
        String osVersion = systemProperties.get("os.version");
        String osArch = systemProperties.get("os.arch");
        return String.format("%s/%s (%s;%s;%s) (%s;%s;%s)", appName, appVersion, osName, osVersion, osArch, javaVendor, javaVersion, javaVendorVersion);
    }

    private static class ProxyAuthenticator extends Authenticator {
        private final Map<String, String> systemProperties;

        private ProxyAuthenticator(Map<String, String> systemProperties) {
            this.systemProperties = systemProperties;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            if (getRequestorType() == RequestorType.PROXY) {
                // Note: Do not use getRequestingProtocol() here, which is "http" even for HTTPS proxies.
                String protocol = getRequestingURL().getProtocol();
                String proxyUser = systemProperties.get(protocol + ".proxyUser");
                if (proxyUser != null) {
                    String proxyPassword = systemProperties.get(protocol + ".proxyPassword");
                    if (proxyPassword == null) {
                        proxyPassword = "";
                    }
                    return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
                }
            }

            return super.getPasswordAuthentication();
        }
    }

    private static class DefaultDownloadProgressListener implements DownloadProgressListener {
        private final Logger logger;
        private final DownloadProgressListener delegate;
        private int previousDownloadPercent;

        public DefaultDownloadProgressListener(Logger logger, DownloadProgressListener delegate) {
            this.logger = logger;
            this.delegate = delegate;
            this.previousDownloadPercent = 0;
        }

        @Override
        public void downloadStatusChanged(URI address, long contentLength, long downloaded) {
            // If the total size of distribution is known, but there's no advanced progress listener, provide extra progress information
            if (contentLength > 0 && delegate == null) {
                appendPercentageSoFar(contentLength, downloaded);
            }

            if (contentLength != downloaded) {
                logger.append(".");
            }

            if (delegate != null) {
                delegate.downloadStatusChanged(address, contentLength, downloaded);
            }
        }

        private void appendPercentageSoFar(long contentLength, long downloaded) {
            try {
                int currentDownloadPercent = 10 * (calculateDownloadPercent(contentLength, downloaded) / 10);
                if (currentDownloadPercent != 0 && previousDownloadPercent != currentDownloadPercent) {
                    logger.append(String.valueOf(currentDownloadPercent)).append('%');
                    previousDownloadPercent = currentDownloadPercent;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private int calculateDownloadPercent(long totalLength, long downloadedLength) {
            return Math.min(100, Math.max(0, (int) ((downloadedLength / (double) totalLength) * 100)));
        }
    }
}
