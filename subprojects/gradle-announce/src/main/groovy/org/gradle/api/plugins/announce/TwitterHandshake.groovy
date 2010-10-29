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
package org.gradle.api.plugins.announce

import org.gradle.api.DefaultTask
import org.gradle.api.plugins.announce.internal.Twitter
import org.gradle.api.tasks.TaskAction
import org.scribe.model.Token
import org.scribe.model.Verifier

class TwitterHandshake extends DefaultTask {
    private Token accessToken

    @TaskAction
    def handshake() {
        if(accessToken){
         return;
        }

        Scanner inScanner = new Scanner(System.in);

        // Obtain the Request Token
        Token requestToken = Twitter.SERVICE.getRequestToken();

        System.out.println("Now go and authorize the Gradle Announce Plugin here:");
        System.out.println(AUTHORIZE_URL + requestToken.getToken());
        System.out.println("And paste the verifier token here");
        System.out.print(">> ");
        Verifier verifier = new Verifier(inScanner.nextLine());
        System.out.println();

        // Trade the Request Token and Verfier for the Access Token
        System.out.println("Trading the Request Token for an Access Token...");
        accessToken = Twitter.SERVICE.getAccessToken(requestToken, verifier);
        System.out.println("Got the Access Token!");

        def properties = new Properties()
        properties.setProperty("twitter.verifier", verifier.getValue())
        properties.setProperty("twitter.token", accessToken.token)
        properties.setProperty("twitter.secret", accessToken.secret)

        properties.store (new FileOutputStream("gradle-announce-twitter.properties"), "auto generated files from gradle")
        System.out.println("Wrote access token to gradle-announce-twitter.properties");
    }

    private static final String AUTHORIZE_URL = "https://twitter.com/oauth/authorize?oauth_token=";
}
