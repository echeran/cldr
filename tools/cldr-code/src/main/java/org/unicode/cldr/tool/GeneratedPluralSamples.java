package org.unicode.cldr.tool;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;

import org.unicode.cldr.tool.GeneratedPluralSamples.Info.Type;
import org.unicode.cldr.tool.Option.Options;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.PatternCache;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo.Count;
import org.unicode.cldr.util.SupplementalDataInfo.PluralType;
import org.unicode.cldr.util.TempPrintWriter;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.ibm.icu.impl.Relation;
import com.ibm.icu.impl.number.DecimalQuantity;
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.text.PluralRules.Operand;

/**
 * Regenerate the plural files. Related classes are:
 * ShowPlurals.printPlurals(english, null, pw); // for doing the charts
 * PluralRulesFactory.getSamplePatterns(locale2); for the samples (to be moved into data)
 * When you change the plural rules, be sure to look at the minimal pairs in PluralRulesFactory.
 */
public class GeneratedPluralSamples {
    private static final double VALUE_TO_CHECK = 7.1;
    static CLDRConfig testInfo = ToolConfig.getToolInstance();
    static Info INFO = new Info();
    private static final boolean THROW = true;

    private static final int SAMPLE_LIMIT = 8;
    private static final int UNBOUNDED_LIMIT = 20;
    private static final String RANGE_SEPARATOR = "~";
    public static final String SEQUENCE_SEPARATOR = ", ";

    static SupplementalDataInfo sInfo = CLDRConfig.getInstance().getSupplementalDataInfo(); // forward declaration

    private static String formatFormC(DecimalQuantity dq) {
        return dq.toExponentString();
    }

    private static String toSampleString(DecimalQuantity dq) {
        if (dq.getPluralOperand(Operand.e) != 0) {
            return dq.toExponentString();
        } else if (dq.getPluralOperand(Operand.c) != 0) {
            return dq.toExponentString();
        } else {
            return dq.toPlainString();
        }
    }

    static class Range implements Comparable<Range> {
        // invariant: visibleFractionDigitCount are the same
        private final DecimalQuantity start;
        private DecimalQuantity end;
        final long visibleFractionDigitCount;

        /**
         * Must only be called if visibleFractionDigitCount are the same.
         */
        public Range(DecimalQuantity start, DecimalQuantity end) {
            this.start = start;
            this.end = end;
            this.visibleFractionDigitCount = (long) start.getPluralOperand(Operand.v);
            if (start.toDouble() < 0 || end.toDouble() < 0) {
                throw new IllegalArgumentException("Must not be negative");
            }
        }

        public Range(Range other) {
            start = other.start;
            end = other.end;
            visibleFractionDigitCount = other.visibleFractionDigitCount;
        }

        @Override
        public int compareTo(Range o) {
            // TODO Auto-generated method stub
            int diff = start == o.start ? 0 : start.toDouble() < o.start.toDouble() ? -1 : 1;
            if (diff != 0) {
                return diff;
            }
            return end == o.end ? 0 : end.toDouble() < o.end.toDouble() ? -1 : 1;
        }

        enum Status {
            inside, rightBefore, other
        }

        Status getStatus(DecimalQuantity ni) {
            long newValue = ni.toLong(true);
            if (newValue < 0) {
                throw new IllegalArgumentException("Must not be negative");
            }
            long startValue = start.toLong(true);
            long endValue = end.toLong(true);
            Status status = startValue <= newValue && newValue <= endValue ? Status.inside
                : endValue + 1 == newValue ? Status.rightBefore
                    : Status.other;
            if (status == Status.rightBefore) {
                endValue = newValue; // just extend it
            }
            return status;
        }

        public StringBuilder format(StringBuilder b) {
            long startValue = start.toLong(true);
            long endValue = end.toLong(true);
            if (visibleFractionDigitCount == 0) {
                b.append(toSampleString(start));
                if (startValue != endValue) {
                    b.append(startValue + 1 == endValue ? SEQUENCE_SEPARATOR : RANGE_SEPARATOR).append(toSampleString(end));
                }
            } else {
                b.append(toSampleString(start));
                if (startValue != endValue) {
                    b.append(startValue + 1 == endValue ? SEQUENCE_SEPARATOR : RANGE_SEPARATOR);
                    b.append(toSampleString(end));
                }
            }
            return b;
        }

        @Override
        public String toString() {
            return format(new StringBuilder()).toString();
        }
    }

    /**
     * Add-only set of ranges.
     */
    static class Ranges {
        @SuppressWarnings("unchecked")
        Set<Range>[] data = new Set[10];
        int size = 0;
        {
            for (int i = 0; i < data.length; ++i) {
                data[i] = new TreeSet<>();
            }
        }

        public Ranges(Ranges other) {
            for (int i = 0; i < data.length; ++i) {
                for (Range range : other.data[i]) {
                    data[i].add(new Range(range));
                }
            }
        }

        public Ranges() {
            // TODO Auto-generated constructor stub
        }

        void add(DecimalQuantity ni) {
            int visibleFracDigits = (int) ni.getPluralOperand(Operand.v);
            Set<Range> set = data[visibleFracDigits];
            for (Range item : set) {
                switch (item.getStatus(ni)) {
                case inside:
                    return;
                case rightBefore:
                    ++size;
                    return;
                }
            }
            set.add(new Range(ni, ni));
            ++size;
        }

        public int size() {
            return size;
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            for (Set<Range> datum : data) {
                for (Range range : datum) {
                    if (b.length() != 0) {
                        b.append(", ");
                    }
                    range.format(b);
                }
            }
            return b.toString();
        }

        public void trim(int sampleLimit) {
            // limit to a total of sampleLimit ranges, *but* also include have at least one of each fraction length
            for (int i = 0; i < data.length; ++i) {
                if (sampleLimit < 2) {
                    sampleLimit = 2;
                }
                for (Iterator<Range> it = data[i].iterator(); it.hasNext();) {
                    it.next();
                    --sampleLimit;
                    if (sampleLimit < 0) {
                        it.remove();
                    }
                }
            }

        }
    }

    static class Info {
        enum Type {
            Warning, Error
        }

        Set<String> bounds = new TreeSet<>();

        public void add(Type type, String string) {
            if (string != null && !string.isEmpty()) {
                if (THROW && type == Type.Error) {
                    throw new IllegalArgumentException(string);
                }
                bounds.add(type + ": " + string);
            }
        }

        public void print() {
            for (String infoItem : bounds) {
                System.err.println(infoItem);
            }
        }
    }

    static final DecimalQuantity CELTIC_SPECIAL = DecimalQuantity_DualStorageBCD.fromExponentString("1000000.0");

    static class DataSample {
        int count;
        int countNoTrailing = -1;
        final Set<DecimalQuantity> noTrailing = new LinkedHashSet<>();
        final Ranges samples = new Ranges();
        final DecimalQuantity[] digitToSample = new DecimalQuantity[20];
        final PluralRules.SampleType sampleType;
        final Set<DecimalQuantity> exponentSamples = new LinkedHashSet<>();
        private boolean isBounded;

        public DataSample(PluralRules.SampleType sampleType) {
            this.sampleType = sampleType;
        }

        @Override
        public String toString() {
            Ranges samples2 = new Ranges(samples);
            for (DecimalQuantity ni : digitToSample) {
                if (ni != null) {
                    samples2.add(ni);
                }
            }
            return format(samples2) + (isBounded ? "" : ", …");
        }

        private String format(Ranges samples2) {
            StringBuilder builder = new StringBuilder().append(samples2);
            int max = 5;
            for (DecimalQuantity exponentSample : exponentSamples) {
                if (builder.length() != 0) {
                    builder.append(", ");
                }
                builder.append(formatFormC(exponentSample));
                if (--max < 0) {
                    break;
                }
            }
            return builder.toString();
        }

        private void add(DecimalQuantity ni) {
            if (ni.getPluralOperand(Operand.e) != 0) {
                exponentSamples.add(ni);
                return;
            }
            ++count;
            if (samples.size() < SAMPLE_LIMIT * 2) {
                samples.add(ni);
            }
            if (noTrailing.size() <= UNBOUNDED_LIMIT * 2) {
                noTrailing.add(ni);
            }
            int digit = getDigit(ni);
            if (digitToSample[digit] == null) {
                digitToSample[digit] = ni;
            }
        }

        @Override
        public boolean equals(Object obj) {
            DataSample other = (DataSample) obj;
            return count == other.count
                && samples.equals(other.samples)
                && digitToSample.equals(other.digitToSample)
                && exponentSamples.equals(other.exponentSamples)
                ;
        }

        @Override
        public int hashCode() {
            // TODO Auto-generated method stub
            return count ^ samples.hashCode() ^ Arrays.asList(digitToSample).hashCode() ^ exponentSamples.hashCode();
        }

        public void freeze(String keyword, PluralRules rule) {
            countNoTrailing = noTrailing.size() + exponentSamples.size();
            //System.out.println(sampleType + ", " + keyword + ", " + countNoTrailing + ", " + rule);
            isBounded = computeBoundedWithSize(keyword, rule);
            if (countNoTrailing > 0) {
                noTrailing.clear(); // to avoid running out of memory.
            }

            if (!isBounded) {
                samples.trim(SAMPLE_LIMIT); // to avoid running out of memory.
            }
        }

        public boolean computeBoundedWithSize(String keyword, PluralRules rule) {
            boolean bounded;
            if (keyword.equals("other")) {
                bounded = noTrailing.size() == 0;
            } else {
                boolean isKnownBounded = rule.computeLimited(keyword, sampleType);
                bounded = isKnownBounded;
                if (countNoTrailing < UNBOUNDED_LIMIT) {
                    bounded = true;
                    if (keyword.equals("many")) {
                        if (countNoTrailing == 1 && noTrailing.contains(CELTIC_SPECIAL)) {
                            bounded = false;
                        }
                    }
                }
                if (bounded != isKnownBounded) {
                    Type infoType = Info.Type.Error;
                    //                        noTrailing.size() == 0 && keyword.equals("other") ||
                    //                        keyword.equals("many") && noTrailing.contains(CELTIC_SPECIAL)
                    //                        ? Info.Type.Warning
                    //                                : Info.Type.Error;
                    INFO.add(infoType, sampleType.toString().toLowerCase(Locale.ENGLISH)
                        + " computation from rule ≠ from items"
                        + "; keyword: " + keyword
                        + "; count: " + noTrailing
                        + "; rule:\n\t" + rule.toString().replace(";", ";\n\t"));
                }
            }
            return bounded;
        }
    }

    class DataSamples {
        private final String keyword; // for debugging
        private final PluralRules rules; // for debugging
        private final DataSample integers = new DataSample(PluralRules.SampleType.INTEGER);
        private final DataSample decimals = new DataSample(PluralRules.SampleType.DECIMAL);
        private boolean boundsComputed;

        DataSamples(String keyword, PluralRules rules) {
            this.keyword = keyword;
            this.rules = rules;
        }

        private void add(DecimalQuantity ni) {
            if (boundsComputed) {
                throw new IllegalArgumentException("Can't call 'add' after 'toString'");
            }
            if (ni.getPluralOperand(Operand.v) == 0) {
                integers.add(ni);
            } else {
                decimals.add(ni);
            }
        }

        @Override
        public String toString() {
            String integersString = integers.toString();
            String decimalsString = type == PluralType.ordinal ? "" : decimals.toString();
            return (integersString.isEmpty() ? "\t\t" : "\t@integer\t" + integersString)
                + (decimalsString.isEmpty() ? "" : "\t@decimal\t" + decimalsString);
        }

        @Override
        public boolean equals(Object obj) {
            DataSamples other = (DataSamples) obj;
            return integers.equals(other.integers) && decimals.equals(other.decimals);
        }

        public void freeze() {
            integers.freeze(keyword, rules);
            if (type != PluralType.ordinal) {
                decimals.freeze(keyword, rules);
            }
            boundsComputed = true;
        }
    }

    //    static boolean computeBounded(String orRule, boolean integer) {
    //        if (orRule == null || orRule.isEmpty()) {
    //            return false;
    //        }
    //        // every 'or' rule must be bounded for the whole thing to be
    //        for (String andRule : orRule.split("\\s*or\\s*")) {
    //            boolean intBounded = false;
    //            boolean decBounded = integer; // when gathering for integers, dec is bounded.
    //            // if any 'and' rule is bounded, then the 'or' rule is
    //            boolean specificInteger;
    //            for (String atomicRule : andRule.split("\\s*and\\s*")) {
    //                char operand = atomicRule.charAt(0);
    //                String remainder = atomicRule.substring(1).trim();
    //                // check to see that the integer values are bounded and that the decimal values are
    //                // once this happens, then the 'and' rule is bounded.
    //
    //                // if the fractional parts must be zero, then this rule is empty for decimals (and thus bounded)
    //                decBounded |= (operand == 'v' || operand == 'w' || operand == 'f' || operand == 't')
    //                        && remainder.equals("is 0");
    //                // if f and t cannot be zero, then this rule is empty for integers (and thus bounded)
    //                intBounded |= (operand == 'f' || operand == 't')
    //                        && (remainder.equals("is 1") || remainder.equals("is not 0")); // should flesh out with parser
    //
    //                if(!atomicRule.contains("mod") && !atomicRule.contains("not")&& !atomicRule.contains("!")) {
    //                    intBounded |= operand == 'i' || operand == 'n';
    //                    decBounded |= operand == 'n' && !atomicRule.contains("within");
    //                }
    //                if (intBounded && decBounded) {
    //                    break;
    //                }
    //            }
    //            if (!intBounded && !decBounded) {
    //                return false;
    //            }
    //        }
    //        return true;
    //    }

    static private int getDigit(DecimalQuantity ni) {
        int result = 0;
        long value = ni.toLong(true);
        do {
            ++result;
            value /= 10;
        } while (value != 0);
        return result;
    }

    private final TreeMap<String, DataSamples> keywordToData = new TreeMap<>();
    private final PluralType type;

    //HACK because ICU doesn't provide a good way to check for whether a rule depends on the 'e' operand

    public final Set<String> SPECIAL_MANY = ImmutableSet.of("fr", "pt", "it", "es");

    GeneratedPluralSamples(PluralInfo pluralInfo, PluralType type, Set<String> equivalentLocales) {
        this.type = type;

        // 9999, powers; no decimals
        collect(pluralInfo, 0, 10_000, 0);
        collect10s(pluralInfo, 10_000, 1_000_000, 0);

        if (type == PluralType.cardinal) {
            // 9999.9, powers .0
            collect(pluralInfo, 0, 10_000, 1);
            collect10s(pluralInfo, 10_000, 1_000_000, 1);

            // 999.99, powers .00
            collect(pluralInfo, 0, 1000, 2);
            collect10s(pluralInfo, 1000, 1_000_000, 2);

            // 99.999, powers .000
            collect(pluralInfo, 0, 100, 3);
            collect10s(pluralInfo, 100, 1_000_000, 3);

            // 9.9999, powers .0000
            collect(pluralInfo, 0, 10, 4);
            collect10s(pluralInfo, 10, 1_000_000, 4);

            // add some exponent samples for French
            // TODO check for any rule with exponent operand and do the same.
            if (!Collections.disjoint(equivalentLocales, SPECIAL_MANY)) {
                final PluralRules pluralRules = pluralInfo.getPluralRules();
                for (int i = 1; i < 15; ++i) {
                    add(pluralRules, DecimalQuantity_DualStorageBCD.fromExponentString(i + "c" + 3));
                    add(pluralRules, DecimalQuantity_DualStorageBCD.fromExponentString(i + "c" + 6));
                    add(pluralRules, DecimalQuantity_DualStorageBCD.fromExponentString(i + "c" + 9));
                    add(pluralRules, DecimalQuantity_DualStorageBCD.fromExponentString(i + ".1c" + 3));
                    add(pluralRules, DecimalQuantity_DualStorageBCD.fromExponentString(i + ".1c" + 6));
                    add(pluralRules, DecimalQuantity_DualStorageBCD.fromExponentString(i + ".1c" + 9));
                    add(pluralRules, DecimalQuantity_DualStorageBCD.fromExponentString(i + ".0001c" + 3));
                    add(pluralRules, DecimalQuantity_DualStorageBCD.fromExponentString(i + ".0000001c" + 6));
                    add(pluralRules, DecimalQuantity_DualStorageBCD.fromExponentString(i + ".0000000001c" + 9));
                }
                int debug = 0;
            }
        }

        for (Entry<String, DataSamples> entry : keywordToData.entrySet()) {
            entry.getValue().freeze();
        }
    }

    private void collect10s(PluralInfo pluralInfo, long start, long end, int decimals) {
        DecimalQuantity_DualStorageBCD endDq = new DecimalQuantity_DualStorageBCD(end);
        endDq.adjustMagnitude(decimals);
        long scaledEnd = endDq.toLong(true);
        for (long i = start; i <= scaledEnd; i *= 10) {
            DecimalQuantity_DualStorageBCD dq = new DecimalQuantity_DualStorageBCD(i);
            dq.adjustMagnitude(-decimals);
            add(pluralInfo, dq);
        }
    }

    private void collect(PluralInfo pluralInfo, long start, long limit, int decimals) {
        DecimalQuantity_DualStorageBCD limitDq = new DecimalQuantity_DualStorageBCD(limit);
        limitDq.adjustMagnitude(decimals);
        long scaledLimit = limitDq.toLong(true);
        for (long i = start; i < scaledLimit; ++i) {
            DecimalQuantity_DualStorageBCD dq = new DecimalQuantity_DualStorageBCD(i);
            dq.adjustMagnitude(-decimals);
            add(pluralInfo, dq);
        }
    }

    private void add(PluralInfo pluralInfo, DecimalQuantity dq) {
        PluralRules pluralRules = pluralInfo.getPluralRules();
        if (CHECK_VALUE && dq.toDouble() == VALUE_TO_CHECK) {
            int debug = 0; // debugging
        }
        String keyword = pluralRules.select(dq);

        INFO.add(Info.Type.Warning, checkForDuplicates(pluralRules, dq));
        add(pluralRules, keyword, dq);
    }

   public void add(PluralRules pluralRules, DecimalQuantity ni) {
       String keyword = pluralRules.select(ni);
       System.out.println(ni + " => " + keyword + ", " + (ni.getPluralOperand(Operand.v) == 0 ? "integer" : "decimal"));
       add(pluralRules, keyword, ni);
   }

    public void add(PluralRules pluralRules, String keyword, DecimalQuantity ni) {
        DataSamples data = keywordToData.get(keyword);
        if (data == null) {
            keywordToData.put(keyword, data = new DataSamples(keyword, pluralRules));
        }
        data.add(ni);
    }

    public static String checkForDuplicates(PluralRules pluralRules, DecimalQuantity ni) {
        // add test that there are no duplicates
        // TODO restore when "CLDR-14206", "Fix CLDR code for FixedDecimal" is done
//        Set<String> keywords = new LinkedHashSet<>();
//        for (String keywordCheck : pluralRules.getKeywords()) {
//            if (pluralRules.matches(ni, keywordCheck)) {
//                keywords.add(keywordCheck);
//            }
//        }
//        if (!keywords.contains("other") || keywords.size() > 2) { // should be either {other, x} or {other}
//            String message = "";
//            for (String keywordCheck : keywords) {
//                message += keywordCheck + ": " + pluralRules.getRules(keywordCheck) + "; ";
//            }
//            return "Duplicate rules with " + ni + ":\t" + message;
//        }
        return null;
    }

    private DataSamples getData(String keyword) {
        return keywordToData.get(keyword);
    }

    @Override
    public boolean equals(Object obj) {
        return keywordToData.equals(((GeneratedPluralSamples) obj).keywordToData);
    }

    @Override
    public int hashCode() {
        return keywordToData.hashCode();
    }

    final static Options myOptions = new Options();
    private static boolean CHECK_VALUE = false;

    enum MyOptions {
        output(".*", CLDRPaths.SUPPLEMENTAL_DIRECTORY, "output data directory"), filter(".*", null, "filter locales"),
        //xml(null, null, "xml file format"),
        multiline(null, null, "multiple lines in file"), sortNew(null, null, "sort without backwards compatible hack");
        // boilerplate
        final Option option;

        MyOptions(String argumentPattern, String defaultArgument, String helpText) {
            option = myOptions.add(this, argumentPattern, defaultArgument, helpText);
        }
    }

    public static void main(String[] args) {
        myOptions.parse(MyOptions.filter, args, true);

        Matcher localeMatcher = !MyOptions.filter.option.doesOccur() ? null : PatternCache.get(MyOptions.filter.option.getValue()).matcher("");
        boolean fileFormat = true; //MyOptions.xml.option.doesOccur();
        final boolean multiline = MyOptions.multiline.option.doesOccur();
        final boolean sortNew = MyOptions.sortNew.option.doesOccur();

        //        computeBounded("n is not 0 and n mod 1000000 is 0", false);
        int failureCount = 0;

        PluralRules pluralRules2 = PluralRules.createRules("one: n is 3..9; two: n is 7..12");
        System.out.println("Check: " + checkForDuplicates(pluralRules2, DecimalQuantity_DualStorageBCD.fromExponentString("8e1")));

        for (PluralType type : PluralType.values()) {
            try (TempPrintWriter out = TempPrintWriter.openUTF8Writer(MyOptions.output.option.getValue(), type == PluralType.cardinal ? "plurals.xml" : "ordinals.xml")) {
                out.print(WritePluralRules.formatPluralHeader(type, "GeneratedPluralSamples"));
                System.out.println("\n");
                Set<String> locales = testInfo.getSupplementalDataInfo().getPluralLocales(type);
                Relation<PluralInfo, String> seenAlready = Relation.of(new TreeMap<PluralInfo, Set<String>>(), TreeSet.class);
                //System.out.println(type + ": " + locales);
                for (String locale : locales) {
//                if (locale.equals("root")) {
//                    continue;
//                }
                    if (localeMatcher != null && !localeMatcher.reset(locale).find()) {
                        continue;
                    }
                    PluralInfo pluralInfo = testInfo.getSupplementalDataInfo().getPlurals(type, locale);
                    //System.out.println(type + ", " + locale + "=>" + pluralInfo);
                    seenAlready.put(pluralInfo, locale);
                }

                // sort if necessary
                Set<Entry<PluralInfo, Set<String>>> sorted = sortNew ? new LinkedHashSet<>()
                    : new TreeSet<>(new HackComparator(type == PluralType.cardinal
                    ? WritePluralRules.HACK_ORDER_PLURALS : WritePluralRules.HACK_ORDER_ORDINALS));
                for (Entry<PluralInfo, Set<String>> entry : seenAlready.keyValuesSet()) {
                    sorted.add(entry);
                }
                Set<String> oldKeywords = Collections.EMPTY_SET;
                Relation<GeneratedPluralSamples, PluralInfo> samplesToPlurals = Relation.of(new LinkedHashMap<GeneratedPluralSamples, Set<PluralInfo>>(),
                    LinkedHashSet.class);
                for (Entry<PluralInfo, Set<String>> entry : sorted) {
                    PluralInfo pluralInfo = entry.getKey();
                    Set<String> equivalentLocales = entry.getValue();

                    CHECK_VALUE = equivalentLocales.contains("pt"); // for debugging

                    String representative = equivalentLocales.iterator().next();

                    PluralRules pluralRules = pluralInfo.getPluralRules();
                    Set<String> keywords = pluralRules.getKeywords();

                    if (fileFormat) {
                        if (!keywords.equals(oldKeywords)) {
                            out.println("\n        <!-- " + keywords.size() + ": " + Joiner.on(",")
                            .join(keywords) + " -->\n");
                            oldKeywords = keywords;
                        }
                        out.println(WritePluralRules.formatPluralRuleHeader(equivalentLocales));
                        System.out.println(type + "\t" + equivalentLocales);
                    }
                    GeneratedPluralSamples samples;
                    try {
                        samples = new GeneratedPluralSamples(pluralInfo, type, equivalentLocales);
                    } catch (Exception e) {
                        out.dontReplaceFile();
                        throw e;
                    }
                    samplesToPlurals.put(samples, pluralInfo);
                    for (String keyword : keywords) {
                        Count count = Count.valueOf(keyword);
                        String rule = pluralInfo.getRule(count);
                        if (rule != null) {
                            // strip original @...
                            int atPos = rule.indexOf('@');
                            if (atPos >= 0) {
                                rule = rule.substring(0, atPos).trim();
                            }
                        }
                        if (rule == null && count != Count.other) {
                            pluralInfo.getRule(count);
                            throw new IllegalArgumentException("No rule for " + count);
                        }
                        if (!fileFormat) {
                            System.out.print(type + "\t" + representative + "\t" + keyword + "\t" + (rule == null ? "" : rule));
                        }
                        DataSamples data = samples.getData(keyword);
                        if (data == null) {
                            System.err.println("***Failure");
                            failureCount++;
                            continue;
                        }
                        if (fileFormat) {
                            out.println(WritePluralRules.formatPluralRule(keyword, rule, data.toString(), multiline));
                        } else {
                            System.out.println(data.toString());
                        }
                    }
                    if (fileFormat) {
                        out.println(WritePluralRules.formatPluralRuleFooter());
                    } else {
                        System.out.println();
                    }
                }
                if (fileFormat) {
                    out.println(WritePluralRules.formatPluralFooter());
                } else {
                    for (Entry<PluralInfo, Set<String>> entry : seenAlready.keyValuesSet()) {
                        if (entry.getValue().size() == 1) {
                            continue;
                        }
                        Set<String> remainder = new LinkedHashSet<>(entry.getValue());
                        String first = remainder.iterator().next();
                        remainder.remove(first);
                        System.err.println(type + "\tEQUIV:\t\t" + first + "\t≣\t" + Joiner.on(", ")
                        .join(remainder));
                    }
                    System.out.println();
                }
                for (Entry<GeneratedPluralSamples, Set<PluralInfo>> entry : samplesToPlurals.keyValuesSet()) {
                    Set<PluralInfo> set = entry.getValue();
                    if (set.size() != 1) {
                        System.err.println("***Failure: Duplicate results " + set);
                        failureCount++;
                    }
                }
                System.out.println("\n");
            }
        }
        if (failureCount > 0) {
            System.err.println("***Failures: " + failureCount);
        }
        INFO.print();
    }

    static class HackComparator implements Comparator<Entry<PluralInfo, Set<String>>> {
        final Map<String, Integer> order;

        HackComparator(Map<String, Integer> order) {
            this.order = order;
        }

        // we get the order of the first items in each of the old rules, and use that order where we can.
        @Override
        public int compare(Entry<PluralInfo, Set<String>> o1, Entry<PluralInfo, Set<String>> o2) {
            Integer firstLocale1 = order.get(o1.getValue().iterator().next());
            Integer firstLocale2 = order.get(o2.getValue().iterator().next());
            if (firstLocale1 != null) {
                if (firstLocale2 != null) {
                    return firstLocale1 - firstLocale2;
                }
                return -1;
            } else if (firstLocale2 != null) {
                return 1;
            } else { // only if BOTH are null, use better comparison
                return o1.getKey().compareTo(o2.getKey());
            }
        }
    }

}
