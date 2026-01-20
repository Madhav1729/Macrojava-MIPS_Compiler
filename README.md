# mjc

A compiler from MacroJava (a subset of Java) to MIPS assembly, written for the CS3310 Compiler Design course at IIT Madras.

```
MacroJava -> MiniJava -> MiniIR -> MicroIR -> MiniRA -> MIPS
```

| Module | Pass |
|---|---|
| Module-1 | Macro expansion (flex, bison) |
| Module-2 | Type checking |
| Module-3 | MiniJava to MiniIR |
| Module-4 | MiniIR to MicroIR |
| Module-5 | MicroIR to MiniRA (liveness analysis, linear-scan register allocation) |
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
mjc Factorial.java --emit=miniir    # stop at minijava, miniir, microir, minira or asm
mjc Factorial.java --save-temps     # keep all intermediate files
```

See `mjc --help` for all options.
