#include "${functionName}.h"

// Include functions from dependencies
<% projectDeps.each { p ->
  sourceFiles.times { %>
#include "${p}lib${it + 1}.h"
<%
  }
} %>

<% functionCount.times { %>
long CPP_${functionName}_${it + 1} () {
  long sum = 1;
  // Call functions defined in dependent projects.
  <% projectDeps.each { p ->
    sourceFiles.times {
      def functionName = "${p}lib${it + 1}"
      functionCount.times {
  %>
  sum += CPP_${functionName}_${it + 1}();
  <%
      }
    }
  } %>
  return sum;
}
<% } %>
