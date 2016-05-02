//CHECKSTYLE:OFF
/*
 * Copyright 2015 the original author or authors.
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

package org.apache.maven.artifact.ant;

import org.apache.maven.wagon.proxy.ProxyInfo;

/**
 * Ant Wrapper for wagon proxy.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id: Proxy.java 524632 2007-04-01 17:05:24Z jvanzyl $
 */
public class Proxy
    extends ProxyInfo
{
    public Proxy()
    {
        super();
    }

    public Proxy(org.apache.maven.settings.Proxy proxy)
    {
        setHost( proxy.getHost() );
        setPort( proxy.getPort() );
        setNonProxyHosts( proxy.getNonProxyHosts() );
        setUserName( proxy.getUsername() );
        setPassword( proxy.getPassword() );
        setType( proxy.getProtocol() );
    }
}
