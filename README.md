# mjc

An optimizing compiler from MacroJava (a subset of Java) to MIPS assembly, written for
the CS3300 and CS6013 compiler courses at IIT Madras.

```
MacroJava -> MiniJava -> TAC -> optimized MiniJava -> MiniIR -> MicroIR -> MiniRA -> MIPS
```

| Module | Pass |
|---|---|
| Module-1 | Macro expansion (flex, bison) |
| Module-2 | Type checking |
| Module-7 | Uninitialized-variable warnings |
| Module-8 | MiniJava to three-address code |
| Module-9 | Constant propagation and dead-code elimination |
| Module-10 | Inlining (points-to analysis) |
| Module-11 | Folding temps back into expressions |
| Module-3 | MiniJava to MiniIR |
| Module-4 | MiniIR to MicroIR |
| Module-5 | MicroIR to MiniRA (register allocation) |
| Module-6 | MiniRA to MIPS |

The languages are described [here](https://www.cse.iitm.ac.in/~krishna/cs3300/subsets.html).

## Build

Requires flex, bison, g++ and JDK 21+. Running programs requires spim.

```bash
make
make test
make install PREFIX=~/.local
```

## Usage

```bash
mjc Factorial.java                  # writes Factorial.s
mjc Factorial.java --run            # compile and run in spim
mjc Factorial.java -O2 --run        # -O1: constant propagation, DCE; -O2: also inlining
mjc Factorial.java --emit=opt -O2   # stop at minijava, tac, opt, miniir, microir, minira or asm
mjc Factorial.java --save-temps     # keep all intermediate files
```

See `mjc --help` for all options.
