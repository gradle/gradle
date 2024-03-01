/*
 * Copyright 2012 the original author or authors.
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


package org.gradle.internal.resource.transfer


import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.resource.ExternalResourceListBuildOperationType
import org.gradle.internal.resource.ExternalResourceName
import spock.lang.Specification

class ProgressLoggingExternalResourceListerTest extends Specification {
    def delegate = Mock(ExternalResourceLister)
    def buildOperationExecutor = Mock(BuildOperationExecutor)
    def context = Mock(BuildOperationContext)
    def lister = new ProgressLoggingExternalResourceLister(delegate, buildOperationExecutor)
    def location = new ExternalResourceName(new URI("https://location/"))

    def "delegates list to delegate and generates build operation"() {
        setup:
        def items = ["a", "b"]
        expectListBuildOperation()

        when:
        def result = lister.list(location)

        then:
        result == items

        1 * delegate.list(location) >> items
    }

    def "returns null when resource does not exist"() {
        setup:
        expectListBuildOperation()

        when:
        def result = lister.list(location)

        then:
        result == null

        1 * delegate.list(location) >> null
    }

    def expectListBuildOperation() {
        1 * buildOperationExecutor.call(_) >> { CallableBuildOperation action ->
            def descriptor = action.description().build()
            assert descriptor.name == "List https://location/"
            assert descriptor.displayName == "List https://location/"
            assert descriptor.progressDisplayName == null

            assert descriptor.details instanceof ExternalResourceListBuildOperationType.Details
            assert descriptor.details.location == location.getUri().toASCIIString()
            action.call(context)
        }
        1 * context.setResult({ it instanceof ExternalResourceListBuildOperationType.Result })
    }
}
