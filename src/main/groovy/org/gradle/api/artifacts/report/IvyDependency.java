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

import java.util.HashSet;
import java.util.Set;

/**
 * Domain object that represents a node in a IvyDependencyGraph.
 *
 * @author Phil Messenger
 */
public class IvyDependency
{
    public Set<IvyDependency> getDependencies()
	{
		return dependencies;
	}

	public void setDependencies(Set<IvyDependency> dependencies)
	{
		this.dependencies = dependencies;
	}

	@Override
	public int hashCode()
	{
		return (getOrganisation() + getName() + getRev()).hashCode();
	}

	private String organisation;
	private String name;
	private String rev;

	private Set<IvyDependency> dependencies = new HashSet<IvyDependency>();

	public IvyDependency(String name, String organisation, String revision)
	{
		this.setOrganisation(organisation);
		this.setRev(revision);
		this.setName(name);
	}

	public String toString()
	{
		return getOrganisation() + ":" + getName() + ":" + getRev();
	}

	public boolean equals(Object o)
	{
		if(o instanceof IvyDependency)
		{
			if(((IvyDependency) o).getOrganisation().equals(getOrganisation()))
			{
				if(((IvyDependency) o).getName().equals(getName()))
				{
					if(((IvyDependency) o).getRev().equals(getRev()))
					{
						return true;
					}
				}

			}
		}

		return false;
	}

	public void addDependency(IvyDependency dependency)
	{
		dependencies.add(dependency);
	}

	public void setOrganisation(String organisation)
	{
		this.organisation = organisation;
	}

	public String getOrganisation()
	{
		return organisation;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public void setRev(String rev)
	{
		this.rev = rev;
	}

	public String getRev()
	{
		return rev;
	}
}
