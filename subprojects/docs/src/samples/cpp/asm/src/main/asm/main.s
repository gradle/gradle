	.section	__TEXT,__text,regular,pure_instructions
	.globl	_main
	.align	4, 0x90
_main:
	pushl	%ebp
	movl	%esp, %ebp
	subl	$8, %esp
	call	_hello
	call	_goodbye
	xorl	%eax, %eax
	addl	$8, %esp
	popl	%ebp
	ret


.subsections_via_symbols
