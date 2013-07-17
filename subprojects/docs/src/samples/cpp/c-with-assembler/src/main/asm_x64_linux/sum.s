        .file   "sum.c"
        .text
        .p2align 4,,15
.globl sum
        .type   sum, @function
sum:
.LFB0:
        .cfi_startproc
        leal    (%rsi,%rdi), %eax
        ret
        .cfi_endproc
.LFE0:
        .size   sum, .-sum
        .ident  "GCC: (Ubuntu/Linaro 4.5.2-8ubuntu4) 4.5.2"
        .section        .note.GNU-stack,"",@progbits
