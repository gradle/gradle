#include <stdio.h>

<% functionCount.times { %>
int CPP_${functionName}_${it + 1} () {
  printf("Hello world!");
  return 0;
}
<% } %>
