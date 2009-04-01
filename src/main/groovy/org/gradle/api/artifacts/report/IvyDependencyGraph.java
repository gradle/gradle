/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.artifacts.report;

import java.util.HashMap;
import java.util.Map;

/**
 * A graph of Ivy dependencies found in a project.
 *
 * @author Phil Messenger
 */
public class IvyDependencyGraph
{
    Map<String, IvyDependency> dependencyCache = new HashMap<String, IvyDependency>();

    IvyDependency root;
    private String conf;

    public void setRoot(IvyDependency root)
    {
        this.root = root;
    }

    public IvyDependency getRoot()
    {
        return root;
    }

    public void add(IvyDependency parent, IvyDependency toAdd)
    {
        parent.addDependency(toAdd);
    }

    /**
     * Finds an existing dependency matching the name, org and revision. If a depdendency can't be found,
     * a new dependency is created.
     * 
     * @param name
     * @param organisation
     * @param revision
     * @return The matching dependency otherwise null
     */
    public IvyDependency findOrCreateDependency(String name, String organisation, String revision)
    {
        String id = buildId(name, organisation, revision);

        IvyDependency dep = dependencyCache.get(id);

        if(dep == null)
        {
            dep = new IvyDependency(name, organisation, revision);

            dependencyCache.put(id, dep);
        }

        return dep;
    }

    private String buildId(String name, String organisation, String revision)
    {
        return organisation + ":" + name + ":" + revision;
    }

    public void setConf(String text)
    {
        this.conf = text;
    }

    public String getConf()
    {
        return this.conf;
    }
}
