//========================================================================
//$Id: SystemProperty.java 3907 2008-10-28 00:39:40Z janb $
//Copyright 2000-2004 Mort Bay Consulting Pty. Ltd.
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

public class SystemProperty
{
    private String name;
    private String value;
    private boolean isSet;
    
    /**
     * @return Returns the name.
     */
    public String getName()
    {
        return this.name;
    }
    /**
     * @param name The name to set.
     */
    public void setName(String name)
    {
        this.name = name;
    }

    public String getKey()
    {
        return this.name;
    }

    public void setKey (String name)
    {
        this.name = name;
    }
    /**
     * @return Returns the value.
     */
    public String getValue()
    {
        return this.value;
    }
    /**
     * @param value The value to set.
     */
    public void setValue(String value)
    {
        this.value = value;
    }

    
    public boolean isSet ()
    {
        return isSet;
    }
    
    /** Set a System.property with this value
     * if it is not already set.
     */
    void setIfNotSetAlready()
    {
        if (System.getProperty(getName()) == null)
        {
            System.setProperty(getName(), (getValue()==null?"":getValue()));
            isSet=true;
        }
    }

}
