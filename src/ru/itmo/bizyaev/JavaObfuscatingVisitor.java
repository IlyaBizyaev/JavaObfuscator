package ru.itmo.bizyaev;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.*;
import ru.itmo.bizyaev.generated.JavaBasicParser;
import ru.itmo.bizyaev.generated.JavaBasicVisitor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class implements {@link JavaBasicVisitor} to format and obfuscate a subset of Java language.
 */
public class JavaObfuscatingVisitor extends AbstractParseTreeVisitor<String> implements JavaBasicVisitor<String> {

    // Map of all identifier name replacements
    private Map<String, String> idReplacementMap;
    // Stack of lists of variables to delete from replacement map after the end of the current scope
    private Deque<ArrayList<String>> scopeCleanupStack;
    // Set of names of fake variables available at this point of code
    private Set<String> fakeVars;
    // Stack of lists of variables to delete from fake vars set after the end of the current scope
    private Deque<ArrayList<String>> fakeVarsStack;
    // Last generated obfuscated variable name
    private String lastIdReplacement;
    // States whether or not current parse context allows variable renaming
    private boolean isRenamingAllowed;
    // Current indentation level (changes with scope entry / quit)
    private int currentIndentLevel;

    JavaObfuscatingVisitor() {
        idReplacementMap = new HashMap<>();
        scopeCleanupStack = new ArrayDeque<>();
        fakeVars = new HashSet<>();
        fakeVarsStack = new ArrayDeque<>();
        lastIdReplacement = "I0100";
        isRenamingAllowed = false;
        currentIndentLevel = 0;
    }

    @Override
    public String visitMethodDeclaration(JavaBasicParser.MethodDeclarationContext ctx) {
        pushScope();
        isRenamingAllowed = true;
        String type = ctx.typeType() != null ? visit(ctx.typeType()) : "void";
        String params = visit(ctx.formalParameters());
        String contents = ctx.block() != null ? " " + visit(ctx.block()) : ";";
        String result = type + " " + ctx.IDENTIFIER().getText() + params + visit(ctx.optionalBrackets()) + contents;
        isRenamingAllowed = false;
        popScope();
        return result;
    }

    @Override
    public String visitConstructorDeclaration(JavaBasicParser.ConstructorDeclarationContext ctx) {
        pushScope();
        isRenamingAllowed = true;
        String result = String.format("%s%s %s", ctx.IDENTIFIER().getText(),
                                                 visit(ctx.formalParameters()), visit(ctx.block()));
        isRenamingAllowed = false;
        popScope();
        return result;
    }

    @Override
    public String visitBlock(JavaBasicParser.BlockContext ctx) {
        currentIndentLevel++;
        pushScope();
        List<String> contents = new ArrayList<>();
        for (JavaBasicParser.BlockStatementContext c : ctx.blockStatement()) {
            String line = indent() + visit(c);
            contents.add(line);
            if (new Random().nextInt(2) == 0) {
                contents.add(indent() + generateRandomUselessStatement());
            }
        }
        popScope();
        currentIndentLevel--;
        return String.format("{\n%s\n%s}", String.join("\n", contents), indent());
    }

    @Override
    public String visitCompilationUnit(JavaBasicParser.CompilationUnitContext ctx) {
        return ctx.typeDeclaration().stream().map(this::visit).collect(Collectors.joining("\n"));
    }

    @Override
    public String visitVariableDeclaratorId(JavaBasicParser.VariableDeclaratorIdContext ctx) {
        return replaceId(ctx.IDENTIFIER(), true) + visit(ctx.optionalBrackets());
    }

    @Override
    public String visitVariableInitializer(JavaBasicParser.VariableInitializerContext ctx) {
        return visit(ctx.getChild(0));
    }

    @Override
    public String visitTypeDeclaration(JavaBasicParser.TypeDeclarationContext ctx) {
        String mods = ctx.modifier().stream().map(RuleContext::getText).collect(Collectors.joining(" "));
        return mods + visit(ctx.classDeclaration()) + ";";
    }

    @Override
    public String visitClassDeclaration(JavaBasicParser.ClassDeclarationContext ctx) {
        return "class " + ctx.IDENTIFIER().getText() + visit(ctx.classBody());
    }

    @Override
    public String visitClassBody(JavaBasicParser.ClassBodyContext ctx) {
        currentIndentLevel++;
        String body = ctx.classBodyDeclaration().stream()
                                                .map((x) -> indent() + visit(x))
                                                .collect(Collectors.joining("\n"));
        currentIndentLevel--;
        return String.format(" {\n%s\n%s}", body, indent());
    }

    @Override
    public String visitEmptyClassBodyDecl(JavaBasicParser.EmptyClassBodyDeclContext ctx) { return ";"; }

    @Override
    public String visitBlockClassBodyDecl(JavaBasicParser.BlockClassBodyDeclContext ctx) {
        return optionalModifier(ctx.STATIC()) + visit(ctx.block());
    }

    @Override
    public String visitMemberClassBodyDecl(JavaBasicParser.MemberClassBodyDeclContext ctx) {
        String mods = ctx.modifier().stream().map(RuleContext::getText).collect(Collectors.joining(" "));
        return mods + (!mods.isEmpty() ? " " : "") + visit(ctx.memberDeclaration());
    }

    @Override
    public String visitMemberDeclaration(JavaBasicParser.MemberDeclarationContext ctx) {
        return visit(ctx.getChild(0));
    }

    @Override
    public String visitFieldDeclaration(JavaBasicParser.FieldDeclarationContext ctx) {
        return String.format("%s %s;", visit(ctx.typeType()), visit(ctx.variableDeclarators()));
    }

    @Override
    public String visitVariableDeclarators(JavaBasicParser.VariableDeclaratorsContext ctx) {
        return ctx.variableDeclarator().stream().map(this::visit).collect(Collectors.joining(", "));
    }

    @Override
    public String visitVariableDeclarator(JavaBasicParser.VariableDeclaratorContext ctx) {
        String varInit = ctx.variableInitializer() == null ? "" : (" = " + visit(ctx.variableInitializer()));
        return visit(ctx.variableDeclaratorId()) + varInit;
    }

    @Override
    public String visitArrayInitializer(JavaBasicParser.ArrayInitializerContext ctx) {
        return ctx.variableInitializer().stream().map(this::visit)
                                        .collect(Collectors.joining(", ", "{", "}"));
    }

    @Override
    public String visitFormalParameters(JavaBasicParser.FormalParametersContext ctx) {
        return "(" + visitIfNotNull(ctx.formalParameterList()) + ")";
    }

    @Override
    public String visitFormalParameterList(JavaBasicParser.FormalParameterListContext ctx) {
        return ctx.formalParameter().stream().map(this::visitFormalParameter).collect(Collectors.joining(", "));
    }

    @Override
    public String visitFormalParameter(JavaBasicParser.FormalParameterContext ctx) {
        String finalMod = optionalModifier(ctx.FINAL());
        return String.format("%s%s %s", finalMod, visit(ctx.typeType()), visit(ctx.variableDeclaratorId()));
    }

    @Override
    public String visitVarDeclBlockStatement(JavaBasicParser.VarDeclBlockStatementContext ctx) {
        String finalMod = optionalModifier(ctx.FINAL());
        return String.format("%s%s %s;", finalMod, visit(ctx.typeType()), visit(ctx.variableDeclarators()));
    }

    @Override
    public String visitStatementBlockStatement(JavaBasicParser.StatementBlockStatementContext ctx) {
        return visit(ctx.statement());
    }

    @Override
    public String visitTypeDeclBlockStatement(JavaBasicParser.TypeDeclBlockStatementContext ctx) {
        return visit(ctx.typeDeclaration());
    }

    @Override
    public String visitNewBlockStatement(JavaBasicParser.NewBlockStatementContext ctx) {
        return visit(ctx.block());
    }

    @Override
    public String visitIfStatement(JavaBasicParser.IfStatementContext ctx) {
        String failBranch = ctx.failBranch == null ? "" : "else " + visit(ctx.failBranch);
        return String.format("if (%s) %s%s", visit(ctx.expression()), visit(ctx.succBranch), failBranch);
    }

    @Override
    public String visitExpressionStatement(JavaBasicParser.ExpressionStatementContext ctx) {
        String ret = ctx.RETURN() == null ? "" : "return";
        String exp = visitIfNotNull(ctx.expression());
        return ret + (!ret.isEmpty() && !exp.isEmpty() ? " " : "") + exp + ";";
    }

    @Override
    public String visitExpressionList(JavaBasicParser.ExpressionListContext ctx) {
        return ctx.expression().stream().map(this::visit).collect(Collectors.joining(", "));
    }

    @Override
    public String visitMethodCall(JavaBasicParser.MethodCallContext ctx) {
        String callee = ctx.IDENTIFIER() != null ? ctx.IDENTIFIER().getText() : "this";
        return String.format("%s(%s)", callee, visit(ctx.expressionList()));
    }

    @Override
    public String visitPrimaryExpression(JavaBasicParser.PrimaryExpressionContext ctx) {
        return visit(ctx.primary());
    }

    @Override
    public String visitCastExpression(JavaBasicParser.CastExpressionContext ctx) {
        return String.format("(%s) %s", visit(ctx.typeType()), visit(ctx.expression()));
    }

    @Override
    public String visitDotExpression(JavaBasicParser.DotExpressionContext ctx) {
        String afterDot = ctx.IDENTIFIER() != null ? ctx.IDENTIFIER().getText() : visit(ctx.methodCall());
        return String.format("%s.%s", visit(ctx.expression()), afterDot);
    }

    @Override
    public String visitSubscriptExpression(JavaBasicParser.SubscriptExpressionContext ctx) {
        return String.format("%s[%s]", visit(ctx.ext), visit(ctx.subscript));
    }

    @Override
    public String visitNewExpression(JavaBasicParser.NewExpressionContext ctx) {
        String toCreate = visitNotNull(ctx.qualifiedName(), ctx.primitiveType());
        String rest = visitNotNull(ctx.arrayCreatorRest(), ctx.classCreatorRest());
        return "new " + toCreate + rest;
    }

    @Override
    public String visitMethodCallExpression(JavaBasicParser.MethodCallExpressionContext ctx) {
        return visit(ctx.methodCall());
    }

    @Override
    public String visitOpExpression(JavaBasicParser.OpExpressionContext ctx) {
        if (ctx.bop != null) {
            return String.format("%s %s %s", visit(ctx.expression(0)),
                                             ctx.bop.getText(),
                                             visit(ctx.expression(1)));
        } else if (ctx.prefix != null) {
            return ctx.prefix.getText() + visit(ctx.expression(0));
        } else {
            return visit(ctx.expression(0)) + ctx.postfix.getText();
        }
    }

    @Override
    public String visitParenthesesPrimary(JavaBasicParser.ParenthesesPrimaryContext ctx) {
        return "(" + visit(ctx.expression()) + ")";
    }

    @Override
    public String visitThisPrimary(JavaBasicParser.ThisPrimaryContext ctx) {
        return "this";
    }

    @Override
    public String visitLiteralPrimary(JavaBasicParser.LiteralPrimaryContext ctx) {
        return ctx.literal().getText();
    }

    @Override
    public String visitIdPrimary(JavaBasicParser.IdPrimaryContext ctx) {
        return replaceId(ctx.IDENTIFIER(), false);
    }

    @Override
    public String visitInitArrayCreatorRest(JavaBasicParser.InitArrayCreatorRestContext ctx) {
        return multiplyString("[]", ctx.LBRACK().size()) + visit(ctx.arrayInitializer());
    }

    @Override
    public String visitExprArrayCreatorRest(JavaBasicParser.ExprArrayCreatorRestContext ctx) {
        String expressions = ctx.expression().stream().map((x) -> "[" + visit(x) + "]").collect(Collectors.joining());
        return expressions + visit(ctx.optionalBrackets());
    }

    @Override
    public String visitClassCreatorRest(JavaBasicParser.ClassCreatorRestContext ctx) {
        return String.format("(%s)%s", visitIfNotNull(ctx.expressionList()), visit(ctx.classBody()));
    }

    @Override
    public String visitTypeType(JavaBasicParser.TypeTypeContext ctx) {
        return visitNotNull(ctx.qualifiedName(), ctx.primitiveType()) + visit(ctx.optionalBrackets());
    }

    @Override
    public String visitQualifiedName(JavaBasicParser.QualifiedNameContext ctx) {
        return ctx.getText();
    }

    @Override
    public String visitOptionalBrackets(JavaBasicParser.OptionalBracketsContext ctx) {
        return multiplyString("[]", ctx.LBRACK().size());
    }

    @Override
    public String visitLiteral(JavaBasicParser.LiteralContext ctx) {
        return ctx.getText();
    }

    @Override
    public String visitModifier(JavaBasicParser.ModifierContext ctx) {
        return ctx.getText();
    }

    @Override
    public String visitPrimitiveType(JavaBasicParser.PrimitiveTypeContext ctx) {
        return ctx.getText();
    }

    private String visitNotNull(ParseTree a, ParseTree b) {
        return a != null ? visit(a) : visit(b);
    }

    private String visitIfNotNull(ParseTree a) {
        return a == null ? "" : visit(a);
    }

    private static String optionalModifier(TerminalNode t) {
        return t == null ? "" : t.getText() + " ";
    }

    // Generates new obfuscated variable names
    private String nextIdReplacement() {
        StringBuilder newIdReplacement = new StringBuilder();
        int i = lastIdReplacement.length() - 1;
        boolean incrementSucceeded = false;
        // Our "digits" are 0, 1, O, I
        for (; i >= 0; --i) {
            switch (lastIdReplacement.charAt(i)) {
                case '0':
                    newIdReplacement.append('1');
                    incrementSucceeded = true;
                    break;
                case 'O':
                    newIdReplacement.append('I');
                    incrementSucceeded = true;
                    break;
                case '1':
                    newIdReplacement.append('O');
                    incrementSucceeded = true;
                    break;
                case 'I':
                    if (i == 0) {
                        newIdReplacement.append("0O");
                        incrementSucceeded = true;
                    } else {
                        newIdReplacement.append("0");
                    }
            }
            if (incrementSucceeded) break;
        }
        lastIdReplacement = lastIdReplacement.substring(0, i) + newIdReplacement.reverse().toString();
        return lastIdReplacement;
    }

    private String generateRandomUselessStatement() {
        final String[] primitiveIntegerTypes = {"int", "long", "short"};

        int action = new Random().nextInt(6);
        String type = primitiveIntegerTypes[new Random().nextInt(primitiveIntegerTypes.length)];
        String randomVar = getRandomFakeVariable();
        if (randomVar == null) {
            action = 0;
        }

        switch (action) {
            case 0: // New variable with random value
                return String.format("%s %s = %d;", type, newFakeVariable(), new Random().nextInt(32768));
            case 1: // New variable assigned from existing fake var
                return String.format("%s %s = (%s) %s;", type, newFakeVariable(), type, randomVar);
            case 2: // Useless instanceof check
                return String.format("boolean %s = (Object)%s instanceof Object;", newFakeVariable(), randomVar);
            case 3: // Self-assignment with ternary
                return String.format("%s = true ? %s : %s;", randomVar, randomVar, randomVar);
            case 4: // Self-assignment
                return String.format("%s = %s;", randomVar, randomVar);
            case 5: // Increase
                return String.format("%s += %d;", randomVar, new Random().nextInt(32768));
            default: // Binary "and" with self
                return String.format("%s &= %s;", randomVar, randomVar);
        }
    }

    private String getRandomFakeVariable() {
        if (fakeVars.isEmpty()) {
            return null;
        }
        List<String> l = new ArrayList<>(fakeVars);
        return l.get(new Random().nextInt(l.size()));
    }

    private String newFakeVariable() {
        String name = nextIdReplacement();
        fakeVars.add(name);
        fakeVarsStack.getLast().add(name);
        return name;
    }

    /* String functions */
    static private String multiplyString(String s, int times) {
        return new String(new char[times]).replace("\0", s);
    }

    private String indent() {
        final int FORMAT_IDENT_SPACES = 4;
        return multiplyString(" ", FORMAT_IDENT_SPACES * currentIndentLevel);
    }

    /* Variable scope logic for identifier replacement */
    private void pushScope() {
        scopeCleanupStack.push(new ArrayList<>());
        fakeVarsStack.push(new ArrayList<>());
    }

    private String replaceId(TerminalNode identifier, boolean canCreateNew) {
        String varName = identifier.getText();
        if (!isRenamingAllowed) {
            return varName;
        }
        if (idReplacementMap.containsKey(varName)) {
            return idReplacementMap.get(varName);
        } else if (canCreateNew) {
            String replacement = nextIdReplacement();
            idReplacementMap.put(varName, replacement);
            scopeCleanupStack.getLast().add(varName);
            return replacement;
        } else {
            return varName;
        }
    }

    private void popScope() {
        idReplacementMap.keySet().removeAll(scopeCleanupStack.pop());
        fakeVars.removeAll(fakeVarsStack.pop());
    }
}