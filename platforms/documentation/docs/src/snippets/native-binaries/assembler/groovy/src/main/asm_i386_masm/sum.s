    .386
    .model    flat

PUBLIC    _sum
_TEXT     SEGMENT
_sum    PROC
    mov    eax, DWORD PTR 4[esp]
    add    eax, DWORD PTR 8[esp]
    ret    0
_sum    ENDP
_TEXT   ENDS
END
