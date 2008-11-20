//========================================================================
//$Id: Proxy.java 215 2006-02-15 09:43:07Z janb $
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

/**
 * Proxy
 * 
 * Provides untyped access to an object of
 * a particular jetty version.
 *
 */
public interface Proxy {
    
    public Object getProxiedObject();

}
