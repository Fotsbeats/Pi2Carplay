.text
.global close_unique_fds
close_unique_fds:
 add x21, x20, #0x14
 add x22, x20, #0x24
next_fd:
 ldr w0, [x21]
 cmn w0, #1
 b.eq advance
 mov w9, #-1
 str w9, [x21]
 add x8, x21, #4
scan:
 cmp x8, x22
 b.hs close_fd
 ldr w10, [x8]
 cmp w10, w0
 b.ne skip
 str w9, [x8]
skip:
 add x8, x8, #4
 b scan
close_fd:
 // Existing close PLT at0x18d40; this instruction is at0x182d4.
 .inst 0x9400029b
advance:
 add x21, x21, #4
 cmp x21, x22
 b.lo next_fd
 nop
