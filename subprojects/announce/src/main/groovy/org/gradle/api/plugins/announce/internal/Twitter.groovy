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

package org.gradle.api.plugins.announce.internal

import org.gradle.api.plugins.announce.Announcer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sun.misc.BASE64Encoder

/**
 * This class allows to send announce messages to Twitter.
 */
class Twitter implements Announcer {
    private static final Logger logger = LoggerFactory.getLogger(Twitter)

    private final String username
    private final String password

    Twitter(String username, String password) {
        this.username = username
        this.password = password
    }

    void send(String title, String message) {
        HttpURLConnection connection

        try {
            connection = new URL("https://twitter.com/statuses/update.xml").openConnection()
            connection.requestMethod = "POST"
            connection.doInput = true
            connection.doOutput = true
            connection.useCaches = false

            String credentials = new BASE64Encoder().encodeBuffer("$username:$password".toString().getBytes("UTF-8")).trim()
            connection.setRequestProperty "Authorization", "Basic " + credentials

            connection.outputStream.withWriter { out ->
                out.write "status=" + URLEncoder.encode(message, "UTF-8")
            }

            logger.info("Successfully tweeted '$message' using account '$username'")
            if (logger.debugEnabled) {
                logger.debug connection.inputStream.getText("UTF-8")
            }
        } finally {
            connection?.disconnect()
        }
    }
}
