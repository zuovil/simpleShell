import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.utils.AttributedString;

import java.util.*;
import java.util.function.Supplier;

public class StringsCompleter implements Completer {
    protected Collection<Candidate>        candidates;
    protected Supplier<Collection<String>> stringsSupplier;

    public StringsCompleter() {
        this(Collections.<Candidate>emptyList());
    }

    public StringsCompleter(Supplier<Collection<String>> stringsSupplier) {
        assert stringsSupplier != null;
        candidates = null;
        this.stringsSupplier = stringsSupplier;
    }

    public StringsCompleter(String... strings) {
        this(Arrays.asList(strings));
    }

    public StringsCompleter(Iterable<String> strings) {
        assert strings != null;
        this.candidates = new ArrayList<>();
        for (String string : strings) {
            candidates.add(new Candidate(AttributedString.stripAnsi(string), string, null, null, " ", null, true));
        }
    }

    public StringsCompleter(Candidate... candidates) {
        this(Arrays.asList(candidates));
    }

    public StringsCompleter(Collection<Candidate> candidates) {
        assert candidates != null;
        this.candidates = new ArrayList<>(candidates);
    }

    @Override
    public void complete(LineReader reader, final ParsedLine commandLine, final List<Candidate> candidates) {
        assert commandLine != null;
        assert candidates != null;
        if (this.candidates != null) {
            candidates.addAll(this.candidates);
        } else {
            for (String string : stringsSupplier.get()) {
                Candidate candidate = new Candidate(string, string, null, null, " ", null, true);
                candidates.add(candidate);
                //candidates.add(new Candidate(string, string, null, null, " ", null, true));
            }
        }
    }

    @Override
    public String toString() {
        String value = candidates != null ? candidates.toString() : "{" + stringsSupplier.toString() + "}";
        return "StringsCompleter" + value;
    }
}
