package ${packageName};

public class ${productionClassName} {
    private final String property;

    public ${productionClassName}(String param) {
        this.property = param;
    }

    public String getProperty() {
        return property;
    }
<% propertyCount.times { %>
    private String prop${it};

    public String getProp${it}() {
        return prop${it};
    }

    public void setProp${it}(String value) {
        prop${it} = value;
    }
<% } %>
}
