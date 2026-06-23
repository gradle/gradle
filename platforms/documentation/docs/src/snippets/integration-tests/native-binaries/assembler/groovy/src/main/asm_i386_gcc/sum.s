    .text
    .globl  _sum
_sum:
    movl    8(%esp), %eax
    addl    4(%esp), %eax
    ret
