    .section    __TEXT,__text,regular,pure_instructions
    .globl  _sum
    .align  4
_sum:
    movl    8(%esp), %eax
    addl    4(%esp), %eax
    ret


.subsections_via_symbols
