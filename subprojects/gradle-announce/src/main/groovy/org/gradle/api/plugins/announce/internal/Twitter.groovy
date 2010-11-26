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


import org.gradle.api.plugins.announce.Announcer
import org.scribe.builder.ServiceBuilder
import org.scribe.builder.api.TwitterApi
import org.scribe.model.OAuthRequest
import org.scribe.model.Response
import org.scribe.model.Token
import org.scribe.model.Verb
import org.scribe.oauth.OAuthService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * This class allows to send announce messages to twitter.
 *
 * @author hackergarten
 */
class Twitter implements Announcer {

    static final OAuthService SERVICE = new ServiceBuilder().provider(TwitterApi).apiKey("c8Ar5l7vpiLURPf1cVO8Q").
            apiSecret("JqUdPRwGjFy086o8evOxOfW4FVRIqERr5sZjv9RvokU").build();

    private static final String TWITTER_UPDATE_URL = "http://twitter.com/statuses/update.xml"

    private static Logger logger = LoggerFactory.getLogger(Twitter)
    private Token token

    Twitter(Token inToken) {
        token = inToken
    }

    public void send(String title, String message) {
        try {
            OAuthRequest request = new OAuthRequest(Verb.POST, TWITTER_UPDATE_URL)

            request.addBodyParameter("status", message)

            SERVICE.signRequest(token, request)
            Response response = request.send()

            def result = response.getBody()
            logger.info result
            logger.info("Successfully send message: [$message] to twitter.")
        } catch (Exception e) {
            logger.warn('Could not send message to twitter', e)
        }

    }
}
