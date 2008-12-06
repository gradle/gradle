package org.gradle.api.internal

import org.gradle.api.plugins.Convention

// todo - figure out how to get this to implement DynamicObject
public class DynamicObjectHelper {

    private final DynamicObject delegateObject
    private final boolean includeDelegateObjectProperties
    Map<String, Object> additionalProperties = [:]
    DynamicObject parent
    Convention convention

    def DynamicObjectHelper(DynamicObject delegateObject) {
        this.includeDelegateObjectProperties = true
        this.delegateObject = delegateObject
    }

    def DynamicObjectHelper(DynamicObject delegateObject, boolean includeDelegateObjectProperties) {
        this.includeDelegateObjectProperties = includeDelegateObjectProperties
        this.delegateObject = delegateObject
    }

    public boolean hasObjectProperty (String name) {
        if (includeDelegateObjectProperties && delegateObject.metaClass.hasProperty(delegateObject, name) != null) {
            return true
        }
        if (convention && convention.hasProperty(name)) {
            return true
        }
        if (parent) {
            return parent.hasProperty(name)
        }
        additionalProperties.containsKey(name)
    }

    public Object getObjectProperty(String name) {
        if (includeDelegateObjectProperties) {
            MetaProperty property = delegateObject.metaClass.hasProperty(delegateObject, name)
            if (property != null) {
                if (property in MetaBeanProperty && property.getter == null) {
                    throw new GroovyRuntimeException("Cannot get the value of write-only property '$name' on $delegateObject.")
                }
                return property.getProperty(delegateObject)
            }
        }
        if (convention && convention.hasProperty(name)) {
            return convention.getProperty(name)
        }
        if (parent) {
            return parent.property(name)
        }
        if (additionalProperties.containsKey(name)) {
            return additionalProperties[name]
        }
        throw new MissingPropertyException("Could not find property '$name' on $delegateObject.")
    }

    public void setObjectProperty(String name, Object value) {
        if (includeDelegateObjectProperties) {
            MetaProperty property = delegateObject.metaClass.hasProperty(delegateObject, name)
            if (property != null) {
                if (property in MetaBeanProperty && property.setter == null) {
                    throw new GroovyRuntimeException("Cannot set the value of read-only property '$name' on $delegateObject.")
                }
                property.setProperty(delegateObject, value)
            }
        }

        if (convention && convention.hasProperty(name)) {
            convention.setProperty(name, value)
        }
        additionalProperties[name] = value
    }

    public Map<String, Object> allProperties ( ) {
        Map<String, Object> properties = [:]
        properties.putAll(additionalProperties)
        if (parent) {
            properties.putAll(parent.properties())
        }
        if (convention) {
            properties.putAll(convention.allProperties)
        }
        if (includeDelegateObjectProperties) {
            properties.putAll(delegateObject.properties)
        }
        properties
    }

    public boolean hasObjectMethod(String name, Object...params) {
        if (delegateObject.metaClass.respondsTo(delegateObject, name, params)) {
            return true
        }
        if (convention && convention.hasMethod(name, params)) {
            return true
        }
        if (parent && parent.hasMethod(name, params)) {
            return true
        }
        false
    }
    
    public Object invokeObjectMethod(String name, Object... params) {
        if (delegateObject.metaClass.respondsTo(delegateObject, name, params)) {
            return delegateObject.metaClass.invokeMethod(delegateObject, name, params);
        }
        if (convention && convention.hasMethod(name, params)) {
            return convention.invokeMethod(name, params)
        }
        if (parent && parent.hasMethod(name, params)) {
            return parent.invokeMethod(name, params)
        }
        throw new MissingMethodException(delegateObject, name, params)
    }

    public DynamicObject getInheritable() {
        DynamicObjectHelper helper = new DynamicObjectHelper(delegateObject, false)
        helper.parent = parent
        helper.convention = convention
        helper.additionalProperties = additionalProperties
        return new HelperBackedDynamicObject(helper)
    }
}

class HelperBackedDynamicObject implements DynamicObject {
    final DynamicObjectHelper helper

    def HelperBackedDynamicObject(helper) {
        this.helper = helper;
    }

    public void setProperty(String name, Object value) {
        throw new MissingPropertyException("Could not find property '$name' inherited from $helper.delegateObject.")
    }

    public boolean hasProperty(String name) {
        helper.hasObjectProperty(name)
    }

    public Object property(String name) {
        helper.getObjectProperty(name)
    }

    public Map<String, Object> properties() {
        helper.allProperties()
    }

    public boolean hasMethod(String name, Object ... params) {
        helper.hasObjectMethod(name, params)
    }

    public Object invokeMethod(String name, Object ... params) {
        helper.invokeObjectMethod(name, params)
    }
}

class MissingMethodException extends groovy.lang.MissingMethodException
{
    def delegateObject

    def MissingMethodException(object, name, arguments) {
        super(name, object.class, arguments);
        delegateObject = object
    }

    public String getMessage() {
        "Could not find method $method() for arguments $arguments on $delegateObject."
    }
}
