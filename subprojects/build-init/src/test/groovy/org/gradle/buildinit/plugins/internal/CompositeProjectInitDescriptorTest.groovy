/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildinit.plugins.internal

import spock.lang.Specification

class CompositeProjectInitDescriptorTest extends Specification {

  def "can compose initdescriptors"(){
        setup:
        ProjectInitDescriptor delegate1 = Mock(ProjectInitDescriptor)
        ProjectInitDescriptor delegate2 = Mock(ProjectInitDescriptor)

        def composableDescriptor = new CompositeProjectInitDescriptor(delegate1, delegate2)

        when:
        composableDescriptor.generateProject()
        then:
        1 * delegate1.generateProject()
        1 * delegate2.generateProject()
  }
}
