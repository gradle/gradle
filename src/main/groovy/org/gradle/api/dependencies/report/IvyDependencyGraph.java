package org.gradle.api.dependencies.report;

import java.util.Map;
import java.util.HashMap;

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
     * @return
     */
    public IvyDependency findOrCreateDependeny(String name, String organisation, String revision)
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
