package searchoteca.service.catalog;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Ajustes comuns aos dados vindos de fora (ISBNdb e Open Library) antes de preencher o formulário. */
final class IsbnFields {

    private IsbnFields() {}

    // tamanhos das colunas de org_books: o que vier maior é cortado para caber
    static final int MAX_TITLE = 100;
    static final int MAX_AUTHOR = 100;
    static final int MAX_PUBLISHER = 50;
    static final int MAX_GENRE = 50;

    private static final Pattern YEAR = Pattern.compile("\\b(1[5-9]\\d{2}|20\\d{2})\\b");

    /** "May 1, 2008", "2008", "2008-05-01" -> 2008. Sem ano reconhecível -> null. */
    static Integer year(String date) {
        if (date == null) return null;
        Matcher m = YEAR.matcher(date);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    static String join(List<String> names) {
        if (names == null || names.isEmpty()) return null;
        String joined = names.stream().filter(Objects::nonNull)
                .map(String::trim).filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(", "));
        return joined.isEmpty() ? null : joined;
    }

    static String cut(String value, int max) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        return v.length() > max ? v.substring(0, max).trim() : v;
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
