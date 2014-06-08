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

import org.gradle.api.artifacts.ClientModule
import org.gradle.util.ConfigureUtil

class ModuleFactoryDelegate {
  ClientModule clientModule
  DependencyFactory dependencyFactory

  def ModuleFactoryDelegate(ClientModule clientModule, DependencyFactory dependencyFactory) {
    this.clientModule = clientModule
    this.dependencyFactory = dependencyFactory
  }

  void prepareDelegation(Closure configureClosure) {
    if (!configureClosure) {
        return
    }
    Closure delegationClosure = {}
    delegationClosure.delegate = clientModule
    delegationClosure.resolveStrategy = Closure.DELEGATE_FIRST
    configureClosure.delegate = delegationClosure
    configureClosure.resolveStrategy = Closure.DELEGATE_FIRST
  }

  void dependency(Object dependencyNotation) {
    dependency(dependencyNotation, null)
  }

  void dependency(Object dependencyNotation, Closure configureClosure) {
    def dependency = dependencyFactory.createDependency(dependencyNotation)
    clientModule.addDependency(dependency)
    ConfigureUtil.configure(configureClosure, dependency)
  }

  void dependencies(Object[] dependencyNotations) {
    dependencyNotations.each { notation ->
      clientModule.addDependency(dependencyFactory.createDependency(notation))
    }
  }

  void module(Object dependencyNotation, Closure configureClosure) {
    clientModule.addDependency(dependencyFactory.createModule(dependencyNotation, configureClosure))
  }
}

