# Project 4 — Team Task Breakdown

Splitting the Geaux compiler's AST→GOTO→C pipeline into 5 roughly independent chunks.

---

## Shared foundation (already in place)

Before picking up your task, read these notes — Task 1 + the driver already landed and made some project-wide decisions that affect everyone.

- **`abstract class GOTO` was renamed to `IRNode`** in `ir.java`, `Visitor.java`, and `Emitter.java`. Use `IRNode` in any new visitor/IR code you write. (Reason: macOS's case-insensitive filesystem treats `GOTO.class` and `Goto.class` as the same file, which silently breaks the build.)
- **Builtin typechecker bypass is already wired in** `JudgementsPass.visitFunExp` for these four names: `printf`, `input`, `readFromFile`, `writeToFile`. Argument counts are not checked for these. If you add a new builtin, add it to that bypass list too.
- **`Emitter` has an `escapeForC()` helper** — ANTLR turns `\n` in `.g` source into a literal newline byte, so string literals have to be escaped back before being written to C. Use this helper for any format/string you emit.
- **The driver pipeline is built.** `compile.sh`/`.bat` compiles the Java. `run.sh`/`.bat <file.g>` runs: parse → typecheck → lower → emit C → gcc → execute. All example programs are invoked the same way.
- **Source layout:** `Absyn/` (AST nodes) and `Typecheck/` (type passes) were copied in from the prog3 skeleton. `CodeGen/Lowerer.java` is the AST→GOTO pass. `CodeGen/Main.java` is the driver.

---

## Task 1 — Core AST→GOTO lowering (expressions + basic statements) ✅ DONE

Write the main visitor/pass that walks the AST and emits GOTO instructions for the "easy" stuff:

- Variable declarations → add to `Program.globals`
- Assignments → `Assign`
- Int/string literals → `Literal`
- Binary ops (`+`, `-`, `*`, `/`, `<`, etc.) → `BinOp`
- Unary ops (`-`, `!`) → `UnaryOp`
- Variable references → `Var`
- `return x;` → `ReturnStmt`

**Done when:** `helloworld.g` and `basics.g` compile to valid C and produce correct output. ✅ Verified.

**Bonus that landed with Task 1:** `if/else` lowering is already implemented in `Lowerer.java` (needed for `basics.g`). Task 2 only has `while` left.

---

## Task 2 — Control flow (while loops) — PARTIALLY DONE

Loops and branches are built by hand from `Label` + `IfStmt` + `Goto`.

- ~~Lower `if (cond) { A } else { B }` using true/false labels + an end label~~ ✅ already done in `Lowerer.java` — study this pattern first
- Lower `while (cond) { body }` using a top label, conditional branch, and a jump back
- Use `Program.getUniqueLabelName()` for every label so nothing collides

**Done when:** `loop.g` and `bubblesort_easy.g` run correctly.

---

## Task 3 — Arrays

All three array IR nodes need real lowering, plus the dynamic-resize behavior.

- `arr[i]` on the right side → `ArrayLoad`
- `arr[i] = x;` → `ArrayStore`, but if `i` is out of bounds, first emit `ArrayAlloc` to grow the array
- Array declarations → add to globals as `INTARRAY` (starts `NULL`, `realloc` treats that as `malloc`)
- Decide how to track current array size (probably a hidden companion variable per array)
- Out-of-bounds *read* should throw an error (print + exit)

**Done when:** `bubblesort_medium.g` runs correctly.

---

## Task 4 — Builtins (ReadFromFile / WriteToFile / Input)

Touches three files: `ir.java`, `Visitor.java`, `Emitter.java`.

- ~~Make the typechecker special-case `printf`, `input`, `readFromFile`, `writeToFile`~~ ✅ already done in `JudgementsPass.visitFunExp`
- ~~Lower `printf(...)` calls to `Printf` IR nodes~~ ✅ already done in `Lowerer.java`
- Flesh out `ReadFromFile`, `WriteToFile`, `Input` the same way `Printf` is done:
  - Add fields + an `accept` method in `ir.java`
  - Add visitor methods in `Visitor.java` (remember: `IRNode`, not `GOTO`)
  - Implement the actual C emission in `Emitter.java` (`fopen` / `fread` / `fwrite` / `fclose` for files, `scanf` for input)
  - Remember to use `escapeForC()` on any string/format literals you emit
- Also wire up the lowering for these three builtins in `Lowerer.java` (the `printf` case already there is a good template)

**Done when:** `fileio.g` and `bubblesort_hard.g` run correctly.

---

## Task 5 — Scoping, renaming, nested functions + video

- Walk the AST and give every variable a unique name using `Program.getUniqueVarName()`. Rewrite every reference to use the new name. This fixes the "all globals collide" problem.
- Flatten nested Geaux functions into top-level C functions (C doesn't allow nested functions)
- ~~Write the driver: parse → typecheck → lower → emit → write `.c` file → invoke `gcc` → run~~ ✅ already done (`CodeGen/Main.java`)
- ~~Write a small script or README with how to build/run each example~~ ✅ already done (`compile.sh`/`.bat`, `run.sh`/`.bat`)
- Own the 10-minute video outline: how the compiler fits together, design decisions, limitations, one concrete efficiency fix

**Done when:** `shadowing.g` and `nested_functions.g` run correctly, and the whole team can run any example with one command.

---

## Suggested dependency order

~~Task 1 should land first~~ ✅ done. Tasks 2, 3, and 4 can now go in parallel. Task 5's renaming can start any time (it's independent), but integration + video happen at the end.
