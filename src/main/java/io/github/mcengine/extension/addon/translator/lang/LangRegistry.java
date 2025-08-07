package io.github.mcengine.extension.addon.translator.lang;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * ISO 639-1 language codes registry.
 * Source reference: https://www.andiamo.co.uk/resources/iso-language-codes/
 */
public class LangRegistry {

    /** Immutable set of known ISO 639-1 two-letter codes (lowercase). */
    private final Set<String> codes;

    public LangRegistry() {
        HashSet<String> s = new HashSet<>();
        Collections.addAll(s,
            "aa","ab","ae","af","ak","am","an","ar","as","av","ay","az",
            "ba","be","bg","bh","bi","bm","bn","bo","br","bs",
            "ca","ce","ch","co","cr","cs","cu","cv","cy",
            "da","de","dv","dz",
            "ee","el","en","eo","es","et","eu",
            "fa","ff","fi","fj","fo","fr","fy",
            "ga","gd","gl","gn","gu","gv",
            "ha","he","hi","ho","hr","ht","hu","hy","hz",
            "ia","id","ie","ig","ii","ik","io","is","it","iu",
            "ja","jv",
            "ka","kg","ki","kj","kk","kl","km","kn","ko","kr","ks","ku","kv","kw","ky",
            "la","lb","lg","li","ln","lo","lt","lu","lv",
            "mg","mh","mi","mk","ml","mn","mr","ms","mt","my",
            "na","nb","nd","ne","ng","nl","nn","no","nr","nv","ny",
            "oc","oj","om","or","os",
            "pa","pi","pl","ps","pt",
            "qu",
            "rm","rn","ro","ru","rw",
            "sa","sc","sd","se","sg","si","sk","sl","sm","sn","so","sq","sr","ss","st","su","sv","sw",
            "ta","te","tg","th","ti","tk","tl","tn","to","tr","ts","tt","tw","ty",
            "ug","uk","ur","uz",
            "ve","vi","vo",
            "wa","wo",
            "xh",
            "yi","yo",
            "za","zh","zu"
        );
        codes = Collections.unmodifiableSet(s);
    }

    /** @return true if {@code code} is a known ISO 639-1 language code. */
    public boolean isValid(String code) { return code != null && codes.contains(code.toLowerCase()); }

    /** @return unmodifiable set of all codes. */
    public Set<String> codes() { return codes; }
}
