/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl.dependencies

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.util.GUtil

/**
 * @author Hans Dockter
 */
class DependencyHandler {
  ConfigurationContainer configurationContainer
  DependencyFactory dependencyFactory

  def DependencyHandler(ConfigurationContainer configurationContainer, DependencyFactory dependencyFactory) {
    this.configurationContainer = configurationContainer;
    this.dependencyFactory = dependencyFactory;
  }

  private Dependency pushDependency(org.gradle.api.artifacts.Configuration configuration, Object notation, Closure configureClosure) {
    Dependency dependency
    if (notation instanceof Dependency) {
      dependency = notation
    } else {
      dependency = dependencyFactory.createDependency(notation, configureClosure)
    }
    configuration.addDependency(dependency)
    dependency
  }

  public Dependency module(Object notation) {
    module(notation, null)
  }

  public Dependency module(Object notation, Closure configureClosure) {
    return dependencyFactory.createModule(notation, configureClosure)
  }

  public def methodMissing(String name, args) {
    Configuration configuration = configurationContainer.findByName(name)
    if (configuration == null) {
      if (!getMetaClass().respondsTo(this, name, args.size())) {
        throw new MissingMethodException(name, this.getClass(), args);
      }
    }

    Object[] normalizedArgs = GUtil.flatten(args as List, false)
    if (normalizedArgs.length == 2 && normalizedArgs[1] instanceof Closure) {
      return pushDependency(configuration, normalizedArgs[0], (Closure) normalizedArgs[1])
    } else if (normalizedArgs.length == 1) {
      return pushDependency(configuration, normalizedArgs[0], (Closure) null)
    }
    normalizedArgs.each { notation ->
      pushDependency(configuration, notation, null)
    }
    return null;
  }
}
