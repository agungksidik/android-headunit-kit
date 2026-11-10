package com.agung.headhome;

import java.util.Comparator;

/** Case-insensitive natural ordering: digit runs compare by value, so "01" < "2" < "10". */
public final class NaturalOrder implements Comparator<String> {

    public static final NaturalOrder INSTANCE = new NaturalOrder();

    @Override
    public int compare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (isDigit(ca) && isDigit(cb)) {
                int si = i;
                while (i < a.length() && isDigit(a.charAt(i))) {
                    i++;
                }
                int sj = j;
                while (j < b.length() && isDigit(b.charAt(j))) {
                    j++;
                }
                String na = stripZeros(a.substring(si, i));
                String nb = stripZeros(b.substring(sj, j));
                if (na.length() != nb.length()) {
                    return na.length() < nb.length() ? -1 : 1;
                }
                int c = na.compareTo(nb);
                if (c != 0) {
                    return c;
                }
            } else {
                int c = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (c != 0) {
                    return c;
                }
                i++;
                j++;
            }
        }
        if (i < a.length()) {
            return 1;
        }
        if (j < b.length()) {
            return -1;
        }
        return a.compareTo(b);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static String stripZeros(String s) {
        int k = 0;
        while (k < s.length() - 1 && s.charAt(k) == '0') {
            k++;
        }
        return s.substring(k);
    }
}
