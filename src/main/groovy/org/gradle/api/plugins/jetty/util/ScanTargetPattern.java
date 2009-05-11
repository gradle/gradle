//========================================================================
//$Id: ScanTargetPattern.java 1888 2007-05-31 08:57:33Z janb $
//Copyright 2006 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at 
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================

package org.gradle.api.plugins.jetty.util;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class ScanTargetPattern
{
    private File _directory;
    private List _includes = Collections.EMPTY_LIST;
    private List _excludes = Collections.EMPTY_LIST;

    /**
     * @return the _directory
     */
    public File getDirectory()
    {
        return _directory;
    }

    /**
     * @param directory the _directory to set
     */
    public void setDirectory(File directory)
    {
        this._directory = directory;
    }
    
    public void setIncludes (List includes)
    {
        _includes= includes;
    }
    
    public void setExcludes(List excludes)
    {
        _excludes = excludes;
    }
    
    public List getIncludes()
    {
        return _includes;
    }
    
    public List getExcludes()
    {
        return _excludes;
    }

}
