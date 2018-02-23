//CHECKSTYLE:OFF
package org.apache.maven.artifact.ant;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * Remote repository type.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id: RemoteRepository.java 615614 2008-01-27 16:06:31Z hboutemy $
 */
public class RemoteRepository
    extends Repository
{
    private String url;

    private Authentication authentication;

    private Proxy proxy;

    private RepositoryPolicy snapshots;

    private RepositoryPolicy releases;

    public String getUrl()
    {
        return ( (RemoteRepository) getInstance() ).url;
    }

    public void setUrl( String url )
    {
        this.url = url;
    }

    public Authentication getAuthentication()
    {
        return ( (RemoteRepository) getInstance() ).authentication;
    }

    public void addAuthentication( Authentication authentication )
    {
        this.authentication = authentication;
    }

    public void addProxy( Proxy proxy )
    {
        this.proxy = proxy;
    }

    public Proxy getProxy()
    {
        return ( (RemoteRepository) getInstance() ).proxy;
    }

    public RepositoryPolicy getSnapshots()
    {
        return ( (RemoteRepository) getInstance() ).snapshots;
    }

    public void addSnapshots( RepositoryPolicy snapshots )
    {
        this.snapshots = snapshots;
    }

    public RepositoryPolicy getReleases()
    {
        return ( (RemoteRepository) getInstance() ).releases;
    }

    public void addReleases( RepositoryPolicy releases )
    {
        this.releases = releases;
    }

    protected String getDefaultId()
    {
        return getUrl();
    }

}
