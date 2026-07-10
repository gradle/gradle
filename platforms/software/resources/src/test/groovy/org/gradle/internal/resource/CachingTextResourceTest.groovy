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

import org.gradle.api.resources.MissingResourceException
import spock.lang.Specification

class CachingTextResourceTest extends Specification {
    def target = Mock(TextResource)
    def resource = new CachingTextResource(target)

    def fetchesAndCachesContentWhenExistenceIsChecked() {
        when:
        assert resource.exists
        assert resource.text == 'content'
        assert resource.exists

        then:
        1 * target.text >> 'content'
        0 * target._
    }

    def existenceCheckReturnsFalseWhenResourceDoesNotExist() {
        when:
        assert !resource.exists

        then:
        1 * target.text >> { throw new MissingResourceException(new URI("somewhere:not-here"), "not found")}
        0 * target._
    }

    def fetchesAndCachesContentWhenContentQueriedAsString() {
        when:
        assert resource.text == 'content'
        assert resource.exists
        assert resource.text == 'content'

        then:
        1 * target.text >> 'content'
        0 * target._
    }

    def fetchesAndCachesContentWhenContentQueriedAsReader() {
        when:
        assert resource.asReader.text == 'content'
        assert resource.exists
        assert resource.asReader.text == 'content'

        then:
        1 * target.text >> 'content'
        0 * target._
    }

    def fetchesAndCachesContentWhenContentIsEmptyIsChecked() {
        when:
        assert !resource.hasEmptyContent
        assert resource.text == 'content'
        assert !resource.hasEmptyContent

        then:
        1 * target.text >> 'content'
        0 * target._
    }

    def hasEmptyContentWhenStringIsEmpty() {
        when:
        assert resource.hasEmptyContent

        then:
        1 * target.text >> ''
        0 * target._
    }
}
