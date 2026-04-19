package Typecheck.Pass;
import Typecheck.Types.*;
import Typecheck.SymbolTable.*;
import java.util.ArrayList;

public class TypeScopePass extends ScopePass<Void> {

   public TypeScopePass(Scope s) {
      super(s);
   }
// Hint: Structs define a new type from their member types.
// 1. Visit the body so member types are fully resolved.
// 2. Collect each member's typeAnnotation.
// 3. Build a LIST type from them.
// 4. Register the struct name in the current scope.
   @Override
	public Void visitStructDecl(Absyn.StructDecl node) {
        super.visitStructDecl(node);
        ArrayList<Type> mems = new ArrayList<>();
        if (node.body != null) {
            for (Absyn.Decl m : node.body.list) {
                // Cast to Parameter (parent of StructMember) to reach the type sub-node
                // whose typeAnnotation was set by TypeAnnotationPass.
                mems.add(((Absyn.Parameter) m).type.typeAnnotation);
            }
        }
        TypeSymbol typeSym = new TypeSymbol(node.name, new LIST(mems));
        this.currentscope.addType(node.name, typeSym);
		return null;
   }
// Hint: Unions define a type that can be any of their member types.
// 1. Visit the body so member types are resolved.
// 2. Collect each member's typeAnnotation.
// 3. Build an OR type from them.
// 4. Register the union name in the current scope.
   @Override
	public Void visitUnionDecl(Absyn.UnionDecl node) {
        super.visitUnionDecl(node);
        ArrayList<Type> mems = new ArrayList<>();
        if (node.body != null) {
            for (Absyn.Decl m : node.body.list) {
                // Cast to Parameter (parent of UnionMember) to reach the type sub-node
                // whose typeAnnotation was set by TypeAnnotationPass.
                mems.add(((Absyn.Parameter) m).type.typeAnnotation);
            }
        }
        TypeSymbol typeSym = new TypeSymbol(node.name, new OR(mems));
        this.currentscope.addType(node.name, typeSym);
		return null;
   }
// Hint: Typedef introduces a new name for an existing type.
// Visit the type first, then register the alias in the current scope.
   @Override
	public Void visitTypedef(Absyn.Typedef node) {
        super.visitTypedef(node);
        if (node.type != null && node.type.typeAnnotation != null) {
            TypeSymbol typeSym = new TypeSymbol(node.name, node.type.typeAnnotation);
            this.currentscope.addType(node.name, typeSym);
        }
		return null;
	}
// Hint: Replace ALIAS types with their real definition.
// Remember that Types can be nested (IE ARRAY(ARRAY(ARRAY(...))) )
// Traverse the whole type to search for Aliases. Once an alias is found,
// look up the type of the alias in the symbol table.
    // This is a function I found helpful to implement. If you have a solution
    // in mind that does not include a helper function, then feel free to ignore
   private void resolveAlias(Type type) {
        if (type == null) return;
        if (type instanceof ALIAS) {
            ALIAS alias = (ALIAS) type;
            try {
                Type aliasedType = this.currentscope.getType(alias.name).type;
                alias.setType(aliasedType);
            } catch (Typecheck.TypeCheckException e) {
                throw new Typecheck.TypeCheckException("Undeclared type alias: " + alias.name);
            }
        } else if (type instanceof ARRAY) {
            resolveAlias(((ARRAY) type).type);
        } else if (type instanceof POINTER) {
            resolveAlias(((POINTER) type).type);
        } else if (type instanceof LIST) {
            for (Type t : ((LIST) type).typelist) {
                resolveAlias(t);
            }
        } else if (type instanceof OR) {
            for (Type o : ((OR) type).options) {
                resolveAlias(o);
            }
        }
   }


// Hint: Visit the brackets and resolve the alias to a type (if the typeAnnotation contains ALIAS)
   @Override
   public Void visitType(Absyn.Type node) {
        super.visitType(node);
        if (node.typeAnnotation != null) {
            resolveAlias(node.typeAnnotation);
        }
		return null;
   }

}
