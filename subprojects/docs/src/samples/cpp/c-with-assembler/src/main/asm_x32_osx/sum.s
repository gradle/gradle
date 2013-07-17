    .section    __TEXT,__text,regular,pure_instructions
    .globl  _sum
    .align  4, 0x90
_sum:
    pushl   %ebp
    movl    %esp, %ebp
    movl    12(%ebp), %eax
    addl    8(%ebp), %eax
    popl    %ebp
    ret


.subsections_via_symbols
