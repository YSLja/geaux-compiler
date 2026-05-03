# CSC 4351 Compiler Project 4

This submission implements the Project 4 Geaux compiler pipeline:

1. Parse Geaux source with the provided lexer/parser resources.
2. Typecheck the AST.
3. Lower the checked program into the starter IR.
4. Emit C code.
5. Compile the generated C with `gcc`.
6. Run the generated binary.

## Requirements

- Java JDK with `javac` and `java`
- `gcc`
- Bash shell on macOS, Linux, or WSL

The required ANTLR/parser jars are included in `prog4_skeleton_code/lib/`.

## Build

From the repository root:

```bash
cd prog4_skeleton_code
./compile.sh
```

If your unzip tool does not preserve executable permissions, use:

```bash
bash compile.sh
```

## Run

From `prog4_skeleton_code/`, pass a `.g` file to `run.sh`:

```bash
./run.sh ../Examples/helloworld.g
./run.sh ../Examples/basics.g
./run.sh ../Examples/bubblesort_medium.g
```

For programs that read from standard input:

```bash
printf "5 1 4 2 3\n" | ./run.sh ../Examples/bubblesort_hard.g
```

Generated `.c` files and binaries are written next to the source `.g` file.

## Included Examples

The `Examples/` folder includes the Project 4 programs used for validation:

- `helloworld.g`
- `basics.g`
- `shadowing.g`
- `nested_functions.g`
- `bubblesort_easy.g`
- `bubblesort_medium.g`
- `bubblesort_hard.g`
- `fileio.g`
- `loop.g`

Additional targeted regressions are in `Examples/regressions/`.

## Implemented Scope

This compiler supports the Project 4 integer, string, function, nested-function, array, loop, break, input, and file-I/O behavior needed by the provided examples and handout scope. Structs, unions, pointers, and non-`int[]` arrays are outside the implemented lowering scope.
