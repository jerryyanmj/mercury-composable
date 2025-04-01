package home.jerry.test.jooq.composable;

public class StringUtil {

    public static int count(String str, char chr) {
        return count(str, chr, 0);
    }

    public static int count(String str, char chr, int start) {
        if (str == null || str.isEmpty()) return 0;
        int n = 0;
        for (int i = str.indexOf(chr, start); i != -1; i = str.indexOf(chr, i+1)) {
            n++;
        }
        return n;
    }

    public static String replaceAll(String value, char[] search, String[] replace) {
        if (value == null || value.isEmpty()) return value;
        StringBuffer sb = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int j = 0;
            do {
                if(c == search[j]) {
                    if (sb == null) {
                        sb = new StringBuffer(value.length());
                        sb.append(value,0,i);
                    }
                    sb.append(replace[j]);
                    break;
                }
                j++;
            } while (j < search.length);
            if (!(j < search.length) && sb != null) {
                sb.append(c);
            }
        }
        return (sb != null) ? sb.toString() : value;
    }

}
