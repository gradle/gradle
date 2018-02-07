#include <stdio.h>

<% sourceFiles.times {
  if (projectName == 'root') {
%>
#include "lib${it + 1}.h"
<% } else { %>
#include "${projectName}lib${it + 1}.h"
<%
  }
} %>

<% if (useMacroIncludes) { %>
#define STDIO <stdio.h>
#include STDIO
<% }%>

int main () {
  long sum = 1;
  <% sourceFiles.times {
    def functionName = "lib${it + 1}"
    if (projectName != 'root') {
      functionName = "${projectName}${functionName}"
    }
    functionCount.times {
  %>
  sum += CPP_${functionName}_${it + 1}();
  <%
    }
  } %>
  printf("You made %d function calls!", sum);
  return 0;
}
