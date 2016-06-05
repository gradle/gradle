/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.plugins.announce.internal;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.gradle.api.plugins.announce.Announcer;
import org.gradle.internal.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * This class allows to send announce messages to Twitter.
 */
public class Twitter implements Announcer {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String username;
    private final String password;

    public Twitter(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public void send(String title, final String message) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) (new URL("https://twitter.com/statuses/update.xml").openConnection());
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);

            String credentials = Base64.encodeBase64String((username + ":" + password).getBytes("UTF-8")).trim();
            connection.setRequestProperty("Authorization", "Basic " + credentials);
            final OutputStream outputStream = connection.getOutputStream();
            IOUtils.write("status=" + URLEncoder.encode(message, "UTF-8"), outputStream);
            IOUtils.closeQuietly(outputStream);

            logger.info("Successfully tweeted \'" + message + "\' using account \'" + username + "\'");
            if (logger.isDebugEnabled()) {
                final InputStream inputStream = connection.getInputStream();
                logger.debug(IOUtils.toString(inputStream, "UTF-8"));
                IOUtils.closeQuietly(inputStream);
            }
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
