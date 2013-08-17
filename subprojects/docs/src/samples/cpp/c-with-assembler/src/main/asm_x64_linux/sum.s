        .text
        .p2align 4,,15
.globl sum
        .type   sum, @function
sum:
        leal    (%rsi,%rdi), %eax
        ret
