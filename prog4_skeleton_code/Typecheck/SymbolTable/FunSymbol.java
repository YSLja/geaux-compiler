package Typecheck.SymbolTable;
import Typecheck.Types.*;

public class FunSymbol extends Symbol {

   public String name;
   public LIST params;
   public Type returnType;

   public FunSymbol(String n, LIST l, Type r) {
      this.name = n;
      this.params = l;
      this.returnType = r;
   }
}
