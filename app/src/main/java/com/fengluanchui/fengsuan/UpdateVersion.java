package com.fengluanchui.fengsuan;

final class UpdateVersion {
    static int compare(String remote, String local) {
        String[] a = parts(remote), b = parts(local);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            long x = i < a.length ? Long.parseLong(a[i]) : 0;
            long y = i < b.length ? Long.parseLong(b[i]) : 0;
            if (x != y) return Long.compare(x, y);
        }
        return 0;
    }
    private static String[] parts(String version) {
        String value = version.replaceFirst("^[vV]", "");
        if (!value.matches("[0-9]{1,9}(\\.[0-9]{1,9}){0,3}")) throw new IllegalArgumentException("Invalid version");
        return value.split("\\.");
    }
}
