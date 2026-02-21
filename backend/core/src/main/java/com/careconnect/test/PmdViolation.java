package com.careconnect.test;
/**
 * Demonstrates PMD violations for CI/CD pipeline testing.
 */
public class PmdViolation {
    /**
     * Triggers empty catch block.
     * @param input input string
     */
    public void emptyCatch(final String input) {
        try {
            int value = Integer.parseInt(input);
            System.out.println(value);
        } catch (NumberFormatException e) {
        }
    }
    /**
     * Triggers unused variable and generic exception catch.
     * @param data the data
     */
    public void unusedVariable(final String data) {
        String unused = "never read";
        int anotherUnused = 42;
        try {
            System.out.println(data.toUpperCase());
        } catch (Exception e) {
            System.out.println("error");
        }
    }
    /**
     * Triggers cyclomatic complexity.
     * @param a first value
     * @param b second value
     * @param c third value
     * @param d fourth value
     * @return result
     */
    public int tooComplex(final int a, final int b, final int c, final int d) {
        if (a > 0) { if (b > 0) { if (c > 0) { if (d > 0) { return 1;
            } else if (d < -10) { return 2; } else { return 3; }
            } else if (c < -10) { return 4; } else { return 5; }
            } else if (b < -10) { return 6; } else { return 7; }
        } else if (a < -10) { return 8; } else { return 9; }
    }
}
