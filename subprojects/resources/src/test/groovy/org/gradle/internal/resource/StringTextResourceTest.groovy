/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.internal.resource

import spock.lang.Specification

class StringTextResourceTest extends Specification {

    def nonEmptyStringResourceHasTextContent() {
        expect:
        def resource = new StringTextResource('displayname', 'text')
        resource.text == 'text'
        resource.exists
        !resource.hasEmptyContent
        resource.contentCached
    }

    def emptyStringResourceHasEmptyContent() {
        expect:
        def resource = new StringTextResource('displayname', '')
        resource.text == ''
        resource.exists
        resource.hasEmptyContent
        resource.contentCached
    }

    def resourceHasNoIdentity() {
        expect:
        def resource = new StringTextResource('displayname', 'text')
        resource.location.file == null
        resource.location.URI == null
        resource.file == null
    }

    def resourceHasDisplayName() {
        expect:
        def resource = new StringTextResource('displayname', 'text')
        resource.displayName == 'displayname'
        resource.location.displayName == 'displayname'
    }
}
