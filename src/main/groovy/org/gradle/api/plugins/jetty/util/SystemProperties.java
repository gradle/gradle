//========================================================================
//$Id: SystemProperties.java 3907 2008-10-28 00:39:40Z janb $
//Copyright 2008 Mort Bay Consulting Pty. Ltd.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SystemProperties
 *
 * Map of name to SystemProperty.
 * 
 * When a SystemProperty instance is added, if it has not
 * been already set (eg via the command line java system property)
 * then it will be set.
 */
public class SystemProperties
{
    Map properties;
    
    public SystemProperties()
    {
        properties = new HashMap();
    }
    
    public void setSystemProperty (SystemProperty prop)
    {
        properties.put(prop.getName(), prop);
        prop.setIfNotSetAlready();
    }
    
    public SystemProperty getSystemProperty(String name)
    {
        return (SystemProperty)properties.get(name);
    }
    
    public List getSystemProperties ()
    {
        return new ArrayList(properties.values());
    }
}
