public class CalculatorRegression {
    private static int count;
    private static void check(String expression, double expected) {
        double actual = new ParserHost.Parser(expression).parse();
        if (Math.abs(actual - expected) > Math.max(1e-25, Math.abs(expected) * 1e-12))
            throw new AssertionError(expression + ": " + actual + " != " + expected);
        count++;
    }
    private static void reject(String expression) {
        try { new ParserHost.Parser(expression).parse(); }
        catch (RuntimeException expected) { count++; return; }
        throw new AssertionError("Accepted invalid expression: " + expression);
    }
    public static void main(String[] args) {
        check("3+6", 9);
        check("9*2", 18);
        check("2+3*4", 14);
        check("(2+3)*4", 20);
        check("-2^2", -4);
        check("(-2)^2", 4);
        check("2^3^2", 512);
        check("0.1+0.2", 0.3);
        check("200+10%", 220);
        check("200-10%", 180);
        check("200*10%", 20);
        check("200/10%", 2000);
        check("200+10%+10%", 242);
        check("200+(10+10)", 220);
        check("200+10%*2", 200.2);
        check("50%", 0.5);
        check("sin(30)", 0.5);
        check("cos(60)", 0.5);
        check("tan(45)", 1);
        check("sqrt(9)", 3);
        check("lg(100)", 2);
        check("ln(e)", 1);
        check("pi*2", Math.PI * 2);
        check("1E-15*2", 2e-15);
        check("1e3+2", 1002);
        check("1E+15/2", 5e14);
        check("√(9)", 3);
        check("3√(27)", 3);
        check("4√(16)", 2);
        check("3√(-8)", -2);
        check("(-3)√(-8)", -0.5);
        check("2+3√(27)*4", 14);
        check("√(9)^2", 9);
        check("3√(27)^2", 9);
        check("3√(8+19)", 3);
        check("√(√(81))", 3);
        check("sin(30)^2", 0.25);
        check("cos(60)^2", 0.25);
        check("lg(100)^2", 4);
        check("sin(cos(0)*30)", 0.5);
        check("SIN(30)", 0.5);
        check("COS(60)", 0.5);
        check("TAN(45)", 1);
        check("LG(100)", 2);
        check("LN(e)", 1);
        for (String value : new String[]{"0√(9)", "2√(-4)", "(-2)√(0)", "√()", "3√()", "sin()", "cos()", "tan()", "lg()", "ln()"})
            reject(value);
        for (String value : new String[]{"", "1/0", "0/0", "sqrt(-1)", "ln(0)",
                "lg(-2)", "tan(90)", "tan(270)", "10^1000", "(2+3", "2+", "1..2", "1E+"})
            reject(value);
        System.out.println("PASS: " + count + " calculator regression cases");
    }
}
