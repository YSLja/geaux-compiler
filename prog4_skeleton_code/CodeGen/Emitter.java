package CodeGen;

import java.util.ArrayList;
import java.util.List;

public class Emitter {

    public static class ProgramEmitter {

        private final ArrayList<Var> globals;
        private final ArrayList<Function> funcs;

        public ProgramEmitter(ArrayList<Var> globals, ArrayList<Function> funcs) {
            this.globals = globals;
            this.funcs = funcs;
        }

        private final InstructionEmitter instrEmitter = new InstructionEmitter();

        public String emitProgram() {
            StringBuilder sb = new StringBuilder();

            sb.append("#include <stdlib.h>\n");
            sb.append("#include <stdio.h>\n\n");
            sb.append(emitHelpers());

            for (Var v : globals) {
                sb.append(v.type.toString()).append(" ").append(v.name).append(";\n");
            }
            sb.append("\n");

            for (Function f : funcs) {
                sb.append(f.returntype).append(" ").append(f.name).append("();\n");
            }
            sb.append("\n");

            for (Function f : funcs) {
                sb.append(f.returntype).append(" ").append(f.name).append("() {\n");
                for (IRNode instr : f.instr) {
                    sb.append(instr.accept(instrEmitter)).append("\n");
                }
                sb.append("}\n\n");
            }
            return sb.toString();
        }

        private String emitHelpers() {
            StringBuilder sb = new StringBuilder();
            sb.append("static void geaux_runtime_error(const char* message) {\n");
            sb.append("    fprintf(stderr, \"%s\\n\", message);\n");
            sb.append("    exit(1);\n");
            sb.append("}\n\n");

            sb.append("static void geaux_array_reserve(int** array, int* size, int requested) {\n");
            sb.append("    int i;\n");
            sb.append("    int oldSize;\n");
            sb.append("    int* grown;\n");
            sb.append("    if (requested < 0) {\n");
            sb.append("        geaux_runtime_error(\"negative array size requested\");\n");
            sb.append("    }\n");
            sb.append("    if (requested <= *size) {\n");
            sb.append("        return;\n");
            sb.append("    }\n");
            sb.append("    oldSize = *size;\n");
            sb.append("    grown = realloc(*array, sizeof(int) * requested);\n");
            sb.append("    if (grown == NULL) {\n");
            sb.append("        geaux_runtime_error(\"array allocation failed\");\n");
            sb.append("    }\n");
            sb.append("    for (i = oldSize; i < requested; i++) {\n");
            sb.append("        grown[i] = 0;\n");
            sb.append("    }\n");
            sb.append("    *array = grown;\n");
            sb.append("    *size = requested;\n");
            sb.append("}\n\n");

            sb.append("static int geaux_array_load(int* array, int size, int index) {\n");
            sb.append("    if (index < 0 || index >= size) {\n");
            sb.append("        geaux_runtime_error(\"array read out of bounds\");\n");
            sb.append("    }\n");
            sb.append("    return array[index];\n");
            sb.append("}\n\n");

            sb.append("static void geaux_array_store(int** array, int* size, int index, int value) {\n");
            sb.append("    if (index < 0) {\n");
            sb.append("        geaux_runtime_error(\"array write out of bounds\");\n");
            sb.append("    }\n");
            sb.append("    if (index >= *size) {\n");
            sb.append("        geaux_runtime_error(\"array write without prior allocation\");\n");
            sb.append("    }\n");
            sb.append("    (*array)[index] = value;\n");
            sb.append("}\n\n");

            sb.append("static int geaux_input_int(void) {\n");
            sb.append("    int value;\n");
            sb.append("    if (scanf(\"%d\", &value) != 1) {\n");
            sb.append("        geaux_runtime_error(\"failed to read int input\");\n");
            sb.append("    }\n");
            sb.append("    return value;\n");
            sb.append("}\n\n");

            sb.append("static void geaux_write_file(const char* path, const char* content) {\n");
            sb.append("    FILE* file = fopen(path, \"wb\");\n");
            sb.append("    if (file == NULL) {\n");
            sb.append("        geaux_runtime_error(\"failed to open output file\");\n");
            sb.append("    }\n");
            sb.append("    if (fputs(content, file) == EOF) {\n");
            sb.append("        fclose(file);\n");
            sb.append("        geaux_runtime_error(\"failed to write file contents\");\n");
            sb.append("    }\n");
            sb.append("    fclose(file);\n");
            sb.append("}\n\n");

            sb.append("static char* geaux_read_file(const char* path) {\n");
            sb.append("    FILE* file = fopen(path, \"rb\");\n");
            sb.append("    long size;\n");
            sb.append("    size_t readCount;\n");
            sb.append("    char* buffer;\n");
            sb.append("    if (file == NULL) {\n");
            sb.append("        geaux_runtime_error(\"failed to open input file\");\n");
            sb.append("    }\n");
            sb.append("    if (fseek(file, 0, SEEK_END) != 0) {\n");
            sb.append("        fclose(file);\n");
            sb.append("        geaux_runtime_error(\"failed to seek input file\");\n");
            sb.append("    }\n");
            sb.append("    size = ftell(file);\n");
            sb.append("    if (size < 0) {\n");
            sb.append("        fclose(file);\n");
            sb.append("        geaux_runtime_error(\"failed to size input file\");\n");
            sb.append("    }\n");
            sb.append("    rewind(file);\n");
            sb.append("    buffer = malloc((size_t) size + 1);\n");
            sb.append("    if (buffer == NULL) {\n");
            sb.append("        fclose(file);\n");
            sb.append("        geaux_runtime_error(\"failed to allocate file buffer\");\n");
            sb.append("    }\n");
            sb.append("    readCount = fread(buffer, 1, (size_t) size, file);\n");
            sb.append("    if (readCount != (size_t) size && ferror(file)) {\n");
            sb.append("        free(buffer);\n");
            sb.append("        fclose(file);\n");
            sb.append("        geaux_runtime_error(\"failed to read file contents\");\n");
            sb.append("    }\n");
            sb.append("    buffer[readCount] = '\\0';\n");
            sb.append("    fclose(file);\n");
            sb.append("    return buffer;\n");
            sb.append("}\n\n");
            return sb.toString();
        }
    }

    public static class InstructionEmitter implements Visitor<String> {

        @Override
        public String visitIRNode(IRNode instr) {
            throw new RuntimeException("Unhandled IR node: " + instr.getClass().getName());
        }

        @Override
        public String visitVar(Var instr) {
            return instr.name;
        }

        @Override
        public String visitLiteral(Literal instr) {
            return switch (instr.type) {
                case INT -> instr.value.toString();
                case STRING -> "\"" + escapeForC(instr.value.toString()) + "\"";
                default -> throw new RuntimeException("Unsupported literal type: " + instr.type);
            };
        }

        @Override
        public String visitBinOp(BinOp instr) {
            return String.format("(%s %s %s)",
                    visit(instr.left),
                    instr.op,
                    visit(instr.right));
        }

        @Override
        public String visitUnaryOp(UnaryOp instr) {
            return String.format("(%s(%s))", instr.op, visit(instr.expr));
        }

        @Override
        public String visitCall(Call instr) {
            return instr.func + "()";
        }

        @Override
        public String visitArrayLoad(ArrayLoad instr) {
            return String.format("geaux_array_load(%s, %s, %s)",
                    visit(instr.array),
                    visit(instr.sizeVar),
                    visit(instr.index));
        }

        @Override
        public String visitAssign(Assign instr) {
            return String.format("%s = %s;",
                    visit(instr.target),
                    visit(instr.value));
        }

        @Override
        public String visitArrayStore(ArrayStore instr) {
            return String.format("geaux_array_store(&%s, &%s, %s, %s);",
                    visit(instr.array),
                    visit(instr.sizeVar),
                    visit(instr.index),
                    visit(instr.value));
        }

        @Override
        public String visitArrayAlloc(ArrayAlloc instr) {
            return String.format("geaux_array_reserve(&%s, &%s, %s);",
                    visit(instr.array),
                    visit(instr.sizeVar),
                    visit(instr.size));
        }

        @Override
        public String visitReadFromFile(ReadFromFile instr) {
            return String.format("geaux_read_file(%s)", visit(instr.path));
        }

        @Override
        public String visitWriteToFile(WriteToFile instr) {
            return String.format("geaux_write_file(%s, %s);",
                    visit(instr.path),
                    visit(instr.content));
        }

        @Override
        public String visitInput(Input instr) {
            return "geaux_input_int()";
        }

        @Override
        public String visitIfStmt(IfStmt instr) {
            return String.format("if (%s) goto %s;\ngoto %s;",
                    visit(instr.cond),
                    instr.trueLabel,
                    instr.falseLabel);
        }

        @Override
        public String visitGoto(Goto instr) {
            return String.format("goto %s;", instr.label);
        }

        @Override
        public String visitLabel(Label instr) {
            return String.format("%s: ;", instr.name);
        }

        @Override
        public String visitReturnStmt(ReturnStmt instr) {
            return String.format("return %s;", visit(instr.value));
        }

        @Override
        public String visitEval(Eval instr) {
            return visit(instr.expr) + ";";
        }

        @Override
        public String visitPrintf(Printf instr) {
            StringBuilder sb = new StringBuilder();
            sb.append("printf(\"").append(escapeForC(instr.format)).append("\"");
            for (IRExpr arg : instr.args) {
                sb.append(", ").append(visit(arg));
            }
            sb.append(");");
            return sb.toString();
        }

        private String escapeForC(String s) {
            return s
                    .replace("\\", "\\\\")
                    .replace("\n", "\\n")
                    .replace("\t", "\\t")
                    .replace("\r", "\\r")
                    .replace("\"", "\\\"");
        }
    }
}
