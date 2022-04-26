package org.gradle.internal.upgrade;

import groovy.lang.DelegatingMetaClass;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import org.codehaus.groovy.runtime.callsite.AbstractCallSite;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Replaces a dynamic Groovy property with the given getters and setters.
 */
class DynamicGroovyPropertyReplacement<T, V> implements Replacement {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicGroovyPropertyReplacement.class);
    private final Class<T> type;
    private final String propertyName;
    private final Function<? super T, ? extends V> getterReplacement;
    private final BiConsumer<? super T, ? super V> setterReplacement;

    public DynamicGroovyPropertyReplacement(Class<T> type, String propertyName, Function<? super T, ? extends V> getterReplacement, BiConsumer<? super T, ? super V> setterReplacement) {
        this.type = type;
        this.propertyName = propertyName;
        this.getterReplacement = getterReplacement;
        this.setterReplacement = setterReplacement;
    }

    @Override
    public Optional<CallSite> decorateCallSite(CallSite callSite) {
        if (callSite.getName().equals(propertyName)) {
            return Optional.of(new AbstractCallSite(callSite) {
                @Override
                public Object callGroovyObjectGetProperty(Object receiver) throws Throwable {
                    if (type.isInstance(receiver)) {
                        LOGGER.info("Calling getter replacement for Groovy property {}.{}", type.getName(), propertyName);
                        return getterReplacement.apply(type.cast(receiver));
                    } else {
                        return super.callGroovyObjectGetProperty(receiver);
                    }
                }

                @Override
                public Object callGetProperty(Object receiver) throws Throwable {
                    if (type.isInstance(receiver)) {
                        LOGGER.info("Calling getter replacement for property {}.{}", type.getName(), propertyName);
                        return getterReplacement.apply(type.cast(receiver));
                    } else {
                        return super.callGetProperty(receiver);
                    }
                }
            });
        }
        return Optional.empty();
    }

    @Override
    public void initializeReplacement() {
        MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(type);
        GroovySystem.getMetaClassRegistry().setMetaClass(type, new PropertySetterMetaClass<>(type, propertyName, setterReplacement, metaClass));
    }

    private static class PropertySetterMetaClass<T, V> extends DelegatingMetaClass {
        private final Class<T> type;
        private final String propertyName;
        private final BiConsumer<? super T, ? super V> setterReplacement;

        public PropertySetterMetaClass(Class<T> type, String propertyName, BiConsumer<? super T, ? super V> setterReplacement, MetaClass delegate) {
            super(delegate);
            this.type = type;
            this.propertyName = propertyName;
            this.setterReplacement = setterReplacement;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setProperty(Object object, String property, Object newValue) {
            if (property.equals(propertyName)) {
                LOGGER.info("Calling setter replacement for property {}.{}", type.getName(), propertyName);
                setterReplacement.accept((T) object, (V) newValue);
            } else {
                super.setProperty(object, property, newValue);
            }
        }
    }
}
