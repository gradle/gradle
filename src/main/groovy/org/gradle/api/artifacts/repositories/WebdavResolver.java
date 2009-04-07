/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api.internal.artifacts.publish.ivycomponents;

import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.gradle.api.internal.artifacts.repositories.WebdavRepository;

/**
 * @author Hans Dockter
 */
public class WebdavResolver extends RepositoryResolver {
    public WebdavResolver() {
         setRepository(new WebdavRepository());
    }

    private WebdavRepository getWebdavRepository() {
        return ((WebdavRepository) getRepository());
    }

    public String getTypeName() {
        return "webdav";
    }

    public void setUserPassword(String userPassword) {
       getWebdavRepository().setUserPassword(userPassword);
    }

    public void setUser(String user) {
        getWebdavRepository().setUser(user);
    }
}
