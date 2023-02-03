
#include "src${sourceIdx}_h.h"

<% includedHeaderCount.times { %>
#include "src${it}_h.h"
<% } %>

<% includedCommonHeaderCount.times { %>
#include "common/include/header${it}.h"
<% } %>

#include <stdio.h>

<% functionCount.times { %>
int C_function_${(it+1)+offset} () {
  printf("Hello world!");
  return 0;
}
<% } %>
