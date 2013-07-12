        .file   "main.c"
        .text
        .p2align 4,,15
.globl main
        .type   main, @function
main:
.LFB0:
        .cfi_startproc
        subq    $8, %rsp
        .cfi_def_cfa_offset 16
        xorl    %eax, %eax
        call    hello
        xorl    %eax, %eax
        call    goodbye
        xorl    %eax, %eax
        addq    $8, %rsp
        .cfi_def_cfa_offset 8
        ret
        .cfi_endproc
.LFE0:
        .size   main, .-main
        .ident  "GCC: (Ubuntu/Linaro 4.5.2-8ubuntu4) 4.5.2"
        .section        .note.GNU-stack,"",@progbits
