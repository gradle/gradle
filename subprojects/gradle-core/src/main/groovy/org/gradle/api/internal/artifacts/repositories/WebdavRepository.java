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
package org.gradle.api.internal.artifacts.repositories;

import org.apache.commons.httpclient.HttpsURL;
import org.apache.ivy.plugins.repository.url.URLRepository;
import org.apache.webdav.lib.WebdavResource;

import java.io.File;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
public class WebdavRepository extends URLRepository {
    private String userPassword;

    private String user;

    public String getUserPassword() {
        return userPassword;
    }

    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void put(File source, String destination, boolean overwrite) throws IOException {
        int fileNameStart = destination.lastIndexOf('/');
        String baseUrl = destination.substring(0, fileNameStart + 1);
        String destinationFileName =  destination.substring(fileNameStart + 1);
        HttpsURL hrl = new HttpsURL(baseUrl);
        hrl.setUserinfo(user, userPassword);
        WebdavResource wdr = new WebdavResource(hrl);
        wdr.putMethod(wdr.getPath() + '/' + destinationFileName, source);
        wdr.close();
    }

    //    Alternative implementation with httpclient only. Unfortunately this is slower.
//
//    public void put(File source, String destination, boolean overwrite) throws IOException {
//        HttpClient client = new HttpClient();
//        HttpState state = client.getState();
//        PutMethod putMethod = new PutMethod(destination);
//        Credentials credentials = new UsernamePasswordCredentials("hans_d", "magus96");
//        state.setCredentials(null, null, credentials);
//        logger.info("Publishing: " + source.getAbsolutePath());
////        putMethod.setRequestEntity(new InputStreamRequestEntity(new FileInputStream(source)));
//        putMethod.setRequestEntity(new FileRequestEntity(source, "application/binary"));
//        try {
//            // execute the GET
//            int status = client.executeMethod(putMethod);
//            // evaluate status
//        } finally {
//            // release any connection resources used by the method
//            putMethod.releaseConnection();
//        }
//    }
}
