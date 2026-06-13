    .text
    .globl main
main:
    sw $fp, -4($sp)
    sw $ra, -8($sp)
    move $fp, $sp
    subu $sp, $sp, 104
    li $t0, 4
    sw $a0, -12($fp)
    sw $v0, -16($fp)
    add $a0, $0, $t0
    addi $v0, $0, 9
    syscall
    move $t1, $v0
    lw $a0, -12($fp)
    lw $v0, -16($fp)
    move $s0, $t1
    li $t2, 4
    sw $a0, -12($fp)
    sw $v0, -16($fp)
    add $a0, $0, $t2
    addi $v0, $0, 9
    syscall
    move $t3, $v0
    lw $a0, -12($fp)
    lw $v0, -16($fp)
    move $s1, $t3
    sw $s1, 0($s0)
    la $t4, Fac_ved_ComputeFac
    sw $t4, 0($s1)
    li $t5, 10
    move $s2, $t5
    li $t6, 0
    move $s3, $t6
    add $t7, $s2, $s3
    move $s4, $t7
    lw $s5, 0($s0)
    lw $s6, 0($s5)
    sw $a0, 52($sp)
    sw $a1, 48($sp)
    sw $a2, 44($sp)
    sw $a3, 40($sp)
    sw $t0, 36($sp)
    sw $t1, 32($sp)
    sw $t2, 28($sp)
    sw $t3, 24($sp)
    sw $t4, 20($sp)
    sw $t5, 16($sp)
    sw $t6, 12($sp)
    sw $t7, 8($sp)
    sw $t8, 4($sp)
    sw $t9, 0($sp)
    move $a0, $s0
    move $a1, $s4
    jalr $s6
    lw $a0, 52($sp)
    lw $a1, 48($sp)
    lw $a2, 44($sp)
    lw $a3, 40($sp)
    lw $t0, 36($sp)
    lw $t1, 32($sp)
    lw $t2, 28($sp)
    lw $t3, 24($sp)
    lw $t4, 20($sp)
    lw $t5, 16($sp)
    lw $t6, 12($sp)
    lw $t7, 8($sp)
    lw $t8, 4($sp)
    lw $t9, 0($sp)
    move $t8, $v0
    move $s7, $t8
    sw $a0, -12($fp)
    sw $v0, -16($fp)
    add $a0, $0, $s7
    addi $v0, $0, 1
    syscall
    la $a0, newl
    li $v0, 4
    syscall
    lw $a0, -12($fp)
    lw $v0, -16($fp)
    lw $ra, -8($fp)
    move $sp, $fp
    lw $fp, -4($fp)
    jr $ra

    .text
    .globl Fac_ved_ComputeFac
Fac_ved_ComputeFac:

    sw $fp, -4($sp)
    sw $ra, -8($sp)
    move $fp, $sp
    subu $sp, $sp, 144
    sw $s0, 84($sp)
    sw $s1, 80($sp)
    sw $s2, 76($sp)
    sw $s3, 72($sp)
    sw $s4, 68($sp)
    sw $s5, 64($sp)
    sw $s6, 60($sp)
    sw $s7, 56($sp)
    sw $a0, 100($sp)
    sw $a1, 112($sp)
    li $t9, 1
    move $s3, $t9
    lw $v0, 112($sp)
    sle $s2, $v0, $s3
    move $s4, $s2
    move $s5, $s4
    beqz $s5 L_ved1
    li $t8, 1
    move $s6, $t8
    lw $v0, 112($sp)
    sne $t7, $v0, $s6
    move $s7, $t7
    move $s5, $s7
L_ved1:
    beqz $s5 L_ved2
    li $s1, 1
    move $t0, $s1
    li $t6, 0
    move $t1, $t6
    add $t4, $t0, $t1
    move $t2, $t4
    move $v1, $t2
    sw $v1, 124($sp)
    j L_ved3
L_ved2:
    li $s0, 1
    move $t3, $s0
    lw $v0, 112($sp)
    sub $t5, $v0, $t3
    move $v1, $t5
    sw $v1, 104($sp)
    lw $v1, 100($sp)
    lw $v0, 0($v1)
    sw $v0, 96($sp)
    lw $v1, 96($sp)
    lw $v0, 0($v1)
    sw $v0, 108($sp)
    lw $v1, 108($sp)
    sw $a0, 52($sp)
    sw $a1, 48($sp)
    sw $a2, 44($sp)
    sw $a3, 40($sp)
    sw $t0, 36($sp)
    sw $t1, 32($sp)
    sw $t2, 28($sp)
    sw $t3, 24($sp)
    sw $t4, 20($sp)
    sw $t5, 16($sp)
    sw $t6, 12($sp)
    sw $t7, 8($sp)
    sw $t8, 4($sp)
    sw $t9, 0($sp)
    lw $v0, 100($sp)
    move $a0, $v0
    lw $v0, 104($sp)
    move $a1, $v0
    jalr $v1
    lw $a0, 52($sp)
    lw $a1, 48($sp)
    lw $a2, 44($sp)
    lw $a3, 40($sp)
    lw $t0, 36($sp)
    lw $t1, 32($sp)
    lw $t2, 28($sp)
    lw $t3, 24($sp)
    lw $t4, 20($sp)
    lw $t5, 16($sp)
    lw $t6, 12($sp)
    lw $t7, 8($sp)
    lw $t8, 4($sp)
    lw $t9, 0($sp)
    move $v1, $v0
    sw $v1, 92($sp)
    lw $v1, 92($sp)
    move $v1, $v1
    sw $v1, 116($sp)
    lw $v1, 116($sp)
    lw $v0, 112($sp)
    mult $v0, $v1
    mflo $v1
    sw $v1, 88($sp)
    lw $v1, 88($sp)
    move $v1, $v1
    sw $v1, 120($sp)
    lw $v1, 120($sp)
    move $v1, $v1
    sw $v1, 124($sp)
L_ved3:
    lw $v1, 124($sp)
    move $v0, $v1
    lw $s0, 84($sp)
    lw $s1, 80($sp)
    lw $s2, 76($sp)
    lw $s3, 72($sp)
    lw $s4, 68($sp)
    lw $s5, 64($sp)
    lw $s6, 60($sp)
    lw $s7, 56($sp)
    lw $ra, -8($fp)
    move $sp, $fp
    lw $fp, -4($fp)
    jr $ra

    .data
    .align 0
newl:    .asciiz "\n"
