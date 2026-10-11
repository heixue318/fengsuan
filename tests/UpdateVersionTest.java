package com.fengluanchui.fengsuan;

public class UpdateVersionTest {
    public static void main(String[] args) {
        String[][] cases = {{"v1.0.22", "1.0.21", "1"}, {"v1.0.21", "1.0.21", "0"},
                {"v1.0.20", "1.0.21", "-1"}, {"v1.0.100", "1.0.99", "1"},
                {"v1.0", "1.0.0", "0"}, {"v2.0.0", "1.9.99", "1"}};
        for (String[] c : cases)
            if (Integer.signum(UpdateVersion.compare(c[0], c[1])) != Integer.parseInt(c[2]))
                throw new AssertionError(c[0]);
        for (String s : new String[]{"", "latest", "v1.0.22-beta", "../1.2", "1..2", "999999999999"}) {
            try { UpdateVersion.compare(s, "1.0"); }
            catch (IllegalArgumentException expected) { continue; }
            throw new AssertionError("Accepted invalid version " + s);
        }
        System.out.println("PASS: 12 update version checks");
    }
}
