        .file   "hello.c"
        .section        .rodata.str1.1,"aMS",@progbits,1
.LC0:
        .string "Hello!"
        .text
        .p2align 4,,15
.globl hello
        .type   hello, @function
hello:
.LFB22:
        .cfi_startproc
        movl    $.LC0, %edi
        jmp     puts
        .cfi_endproc
.LFE22:
        .size   hello, .-hello
        .ident  "GCC: (Ubuntu/Linaro 4.5.2-8ubuntu4) 4.5.2"
        .section        .note.GNU-stack,"",@progbits
