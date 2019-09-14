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
package org.gradle.api.java.archives.internal;

import com.google.common.base.Utf8;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.java.archives.ManifestException;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.jar.Attributes.Name;

public class DefaultAttributes extends AbstractMap<String, Object> implements Attributes {
    private static final LoadingCache<String, Name> KNOWN_NAMES =
        CacheBuilder.newBuilder()
            .maximumSize(1000)
            .build(CacheLoader.from(Name::new));

    protected final Map<Name, Object> attributes = new LinkedHashMap<>();

    private transient KeySet keySet;
    private transient EntrySet entrySet;

    protected Name keyFor(String name) {
        return KNOWN_NAMES.getUnchecked(name);
    }

    @Override
    public String toString() {
        return attributes.toString();
    }

    @Override
    public int size() {
        return attributes.size();
    }

    @Override
    public boolean isEmpty() {
        return attributes.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof String) {
            return attributes.containsKey(keyFor((String) key));
        }
        return attributes.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return attributes.containsValue(value);
    }

    @Override
    public Object get(Object key) {
        if (key instanceof String) {
            return attributes.get(keyFor((String) key));
        }
        return attributes.get(key);
    }

    @Override
    public Object put(String key, Object value) {
        if (key == null) {
            // NPE is the specified exception type (see Map#put javadoc)
            throw new NullPointerException("The key of a manifest attribute must not be null.");
        }
        if (value == null) {
            // NPE is the specified exception type (see Map#put javadoc)
            throw new NullPointerException(String.format("The value of a manifest attribute must not be null (Key=%s).", key));
        }
        Name name;
        if (key.length() > 70) {
            // OpenJDK does not clarify the reason manifest attribute is invalid,
            // so we throw a user-understandable message here.
            // Note: the specification requires that the keys should fit into 70 UTF-8 bytes,
            // however the keys are limited to [-_A-Za-z0-9] characters.
            // That is why we can check String#length() and it would still be fine.
            throw new ManifestException(String.format("The Key=%s violates the Manifest spec: key should not exceed 70 bytes, actual length is %d bytes in UTF-8", key, Utf8.encodedLength(key)));
        }
        try {
            name = KNOWN_NAMES.getUnchecked(key);
        } catch (UncheckedExecutionException e) {
            throw new ManifestException(String.format("The Key=%s violates the Manifest spec!", key), e);
        }
        return attributes.put(name, value);
    }

    @Override
    public Object remove(Object key) {
        if (key instanceof String) {
            return attributes.remove(keyFor((String) key));
        }
        return attributes.remove(key);
    }

    @Override
    public void clear() {
        attributes.clear();
    }

    @Override
    public Set<String> keySet() {
        KeySet ks = keySet;
        if (ks == null) {
            ks = new KeySet();
            keySet = ks;
        }
        return ks;
    }

    @Override
    public Collection<Object> values() {
        return attributes.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        EntrySet es = entrySet;
        if (es == null) {
            es = new EntrySet();
            entrySet = es;
        }
        return es;
    }

    private abstract class BaseSet<T> extends AbstractSet<T> {
        @Override
        public int size() {
            return DefaultAttributes.this.size();
        }

        @Override
        public void clear() {
            DefaultAttributes.this.clear();
        }

        @Override
        public Spliterator<T> spliterator() {
            return Spliterators.spliterator(this, Spliterator.SIZED | Spliterator.DISTINCT |
                Spliterator.ORDERED);
        }
    }

    private class EntrySet extends BaseSet<Entry<String, Object>> {
        @Override
        public Iterator<Entry<String, Object>> iterator() {
            return new EntrySetIterator();
        }

        @Override
        public boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Object key = ((Entry) o).getKey();
                Object prevValue = get(key);
                if (Objects.equals(prevValue, ((Entry) o).getValue())) {
                    DefaultAttributes.this.remove(key);
                    return true;
                }
            }
            return false;
        }
    }

    private class KeySet extends BaseSet<String> {
        @Override
        public Iterator<String> iterator() {
            return new KeyIterator();
        }

        @Override
        public boolean remove(Object o) {
            // Value cannot be null, so it is safe to assume != means "there was an entry"
            return DefaultAttributes.this.remove(o) != null;
        }
    }

    private abstract class EntryIterator<T> implements Iterator<T> {
        private Iterator<Entry<Name, Object>> delegate = attributes.entrySet().iterator();

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public void remove() {
            delegate.remove();
        }

        protected Map.Entry<Name, Object> nextEntry() {
            return delegate.next();
        }
    }

    private class KeyIterator extends EntryIterator<String> {
        @Override
        public String next() {
            return nextEntry().getKey().toString();
        }
    }

    private class EntrySetIterator extends EntryIterator<Map.Entry<String, Object>> {
        @Override
        public Map.Entry<String, Object> next() {
            return new CaseInsensitiveEntry(nextEntry());
        }
    }

    /**
     * Exposes {@code Entry<Name, Object>} as if it was {@code Entry<String, Object>}
     */
    class CaseInsensitiveEntry implements Map.Entry<String, Object> {
        private final Map.Entry<Name, Object> delegate;

        CaseInsensitiveEntry(Entry<Name, Object> delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getKey() {
            return delegate.getKey().toString();
        }

        @Override
        public Object getValue() {
            return delegate.getValue();
        }

        @Override
        public Object setValue(Object value) {
            if (value == null) {
                throw new NullPointerException(String.format("The value of a manifest attribute must not be null (Key=%s).",
                    delegate.getKey()
                ));
            }
            return attributes.put(delegate.getKey(), value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entry)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Entry<String, Object> other = (Entry<String, Object>) o;
            return Objects.equals(getKey(), other.getKey()) &&
                Objects.equals(getValue(), other.getValue());
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
