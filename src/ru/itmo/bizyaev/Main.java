package ru.itmo.bizyaev;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import ru.itmo.bizyaev.generated.JavaBasicLexer;
import ru.itmo.bizyaev.generated.JavaBasicParser;

public class Main {

    public static void main(String[] args) {
        if (args == null || args.length != 2 || args[0] == null || args[1] == null) {
            System.err.println("Expected 2 arguments, paths of input and output files");
            return;
        }

        try {
            String obfuscatedCode = obfuscate(args[0]);
            System.out.println(obfuscatedCode);
            try {
                Files.newBufferedWriter(Paths.get(args[1]), StandardCharsets.UTF_8).write(obfuscatedCode);
            } catch (IOException e) {
                System.err.println("Failed to write obfuscation result: " + e.getMessage());
            }
        } catch (IOException e) {
            System.err.println("Failed to read the input file: " + e.getMessage());
        }
    }

    private static String obfuscate(String inputFilePath) throws IOException {
        JavaBasicLexer lexer = new JavaBasicLexer(CharStreams.fromFileName(inputFilePath, StandardCharsets.UTF_8));
        JavaBasicParser parser = new JavaBasicParser(new CommonTokenStream(lexer));
        JavaObfuscatingVisitor visitor = new JavaObfuscatingVisitor();
        return visitor.visit(parser.compilationUnit()); // .exception
    }
}
