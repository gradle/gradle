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

import org.apache.tools.ant.ProjectComponent;

/**
 * Base class for a repository policy.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id: RepositoryPolicy.java 649782 2008-04-19 09:42:38Z hboutemy $
 */
public class RepositoryPolicy
    extends ProjectComponent
{
    private String updatePolicy;

    private String checksumPolicy;

    private boolean enabled = true;

    public String getUpdatePolicy()
    {
        return updatePolicy;
    }

    public void setUpdatePolicy( String updatePolicy )
    {
        this.updatePolicy = updatePolicy;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled( boolean enabled )
    {
        this.enabled = enabled;
    }

    public String getChecksumPolicy()
    {
        return checksumPolicy;
    }

    public void setChecksumPolicy( String checksumPolicy )
    {
        this.checksumPolicy = checksumPolicy;
    }
}
