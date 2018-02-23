package ${packageName}

class ${productionClassName}(val property: String) {
<% propertyCount.times { %>
var prop${it}: String
<% } %>
}
