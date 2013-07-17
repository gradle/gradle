    TITLE    Z:\dev\gradleware\gradle-windows\windows-test\asm\sum.c
    .686P
    .XMM
    include   listing.inc
    .model    flat

INCLUDELIB LIBCMT
INCLUDELIB OLDNAMES

PUBLIC    _sum
_TEXT     SEGMENT
_a$ = 8
_b$ = 12
_sum    PROC
    push   ebp
    mov    ebp, esp
    mov    eax, DWORD PTR _a$[ebp]
    add    eax, DWORD PTR _b$[ebp]
    pop    ebp
    ret    0
_sum    ENDP
_TEXT   ENDS
END
