	.section	__TEXT,__text,regular,pure_instructions
	.globl	_goodbye
	.align	4, 0x90
_goodbye:
	pushl	%ebp
	movl	%esp, %ebp
	subl	$8, %esp
	movl	$L_.str, (%esp)
	call	_printf
	addl	$8, %esp
	popl	%ebp
	ret

	.section	__TEXT,__cstring,cstring_literals
L_.str:
	.asciz	 "Goodbye.\n"


.subsections_via_symbols
