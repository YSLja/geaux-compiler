# Project 4 — Team Task Breakdown

Splitting the Geaux compiler's AST→GOTO→C pipeline into 5 roughly independent chunks.

---

## Task 1 — Core AST→GOTO lowering (expressions + basic statements)

Write the main visitor/pass that walks the AST and emits GOTO instructions for the "easy" stuff:

- Variable declarations → add to `Program.globals`
- Assignments → `Assign`
- Int/string literals → `Literal`
- Binary ops (`+`, `-`, `*`, `/`, `<`, etc.) → `BinOp`
- Unary ops (`-`, `!`) → `UnaryOp`
- Variable references → `Var`
- `return x;` → `ReturnStmt`

**Done when:** `helloworld.g` and `basics.g` compile to valid C and produce correct output.

---

## Task 2 — Control flow (if/else + while loops)

Loops and branches have to be built by hand from `Label` + `IfStmt` + `Goto`. This person owns that lowering.

- Lower `if (cond) { A } else { B }` using true/false labels + an end label
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

## Task 4 — Builtins (printf bypass + ReadFromFile / WriteToFile / Input)

Touches three files: `ir.java`, `Visitor.java`, `Emitter.java`.

- Make the typechecker special-case `printf`, `input`, `readFromFile`, `writeToFile` so they don't error as "undefined function"
- Lower `printf(...)` calls to `Printf` IR nodes (already defined)
- Flesh out `ReadFromFile`, `WriteToFile`, `Input` the same way `Printf` is done:
  - Add fields + an `accept` method in `ir.java`
  - Add visitor methods in `Visitor.java`
  - Implement the actual C emission in `Emitter.java` (`fopen` / `fread` / `fwrite` / `fclose` for files, `scanf` for input)

**Done when:** `fileio.g` and `bubblesort_hard.g` run correctly.

---

## Task 5 — Scoping, renaming, nested functions + integration

This is the glue person.

- Walk the AST and give every variable a unique name using `Program.getUniqueVarName()`. Rewrite every reference to use the new name. This fixes the "all globals collide" problem.
- Flatten nested Geaux functions into top-level C functions (C doesn't allow nested functions)
- Write the driver: parse → typecheck → lower → emit → write `.c` file → invoke `gcc` → run
- Write a small script or README with how to build/run each example
- Own the 10-minute video outline: how the compiler fits together, design decisions, limitations, one concrete efficiency fix

**Done when:** `shadowing.g` and `nested_functions.g` run correctly, and the whole team can run any example with one command.

---

## Suggested dependency order

Task 1 should land first — everyone else builds on the pass it creates. Tasks 2, 3, and 4 can go in parallel after that. Task 5's renaming can start early (it's independent), but the integration piece and video happen at the end.
