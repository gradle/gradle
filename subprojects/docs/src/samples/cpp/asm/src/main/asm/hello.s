	.section	__TEXT,__text,regular,pure_instructions
	.globl	_main
	.align	4, 0x90
_main:
	pushl	%ebp
	movl	%esp, %ebp
	subl	$8, %esp
	movl	$L_.str, (%esp)
	call	_puts
	xorl	%eax, %eax
	addl	$8, %esp
	popl	%ebp
	ret

	.section	__TEXT,__cstring,cstring_literals
L_.str:
	.asciz	 "Hello world!\n"


.subsections_via_symbols
