        .file   "goodbye.c"
        .section        .rodata.str1.1,"aMS",@progbits,1
.LC0:
        .string "Goodbye."
        .text
        .p2align 4,,15
.globl goodbye
        .type   goodbye, @function
goodbye:
.LFB22:
        .cfi_startproc
        movl    $.LC0, %edi
        jmp     puts
        .cfi_endproc
.LFE22:
        .size   goodbye, .-goodbye
        .ident  "GCC: (Ubuntu/Linaro 4.5.2-8ubuntu4) 4.5.2"
        .section        .note.GNU-stack,"",@progbits
