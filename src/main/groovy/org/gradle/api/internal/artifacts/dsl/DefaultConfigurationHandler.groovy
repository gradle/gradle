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
 
package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.IvyService
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.configurations.ResolverProvider
import org.gradle.util.ConfigureUtil

/**
 * @author Hans Dockter
 */
class DefaultConfigurationHandler extends DefaultConfigurationContainer {


    def DefaultConfigurationHandler(IvyService ivyService, ResolverProvider resolverProvider, DependencyMetaDataProvider dependencyMetaDataProvider) {
    super(ivyService, resolverProvider, dependencyMetaDataProvider)
  }

  Configuration getByName(String name) {
    Configuration configuration = find(name)
    if (configuration == null) {
      return add(name)
    }
    return configuration
  }

  public def methodMissing(String name, args) {
    if (!(args.length == 1 && args[0] instanceof Closure)) {
      if (!getMetaClass().respondsTo(this, name, args.size())) {
        throw new MissingMethodException(name, this.getClass(), args);
      }
      return getMetaClass().invokeMethod(this, name, args);
    }
    Configuration configuration = getByName(name)
    ConfigureUtil.configure(args[0], configuration)
  }
}
