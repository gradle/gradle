#include <stdio.h>

<% functionCount.times { %>
int ${functionName}_${it+1} () {
  printf("Hello world!");
  return 0;
}
<% } %>
