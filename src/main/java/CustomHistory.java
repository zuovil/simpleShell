import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.utils.Log;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import static org.jline.reader.LineReader.HISTORY_IGNORE;
import static org.jline.reader.impl.ReaderUtils.*;
import static org.jline.reader.impl.ReaderUtils.isSet;

public class CustomHistory implements History {

    /**
     * Default maximum number of history entries to keep in memory.
     * This value is used when the {@link LineReader#HISTORY_SIZE} variable is not set.
     */
    public static final int DEFAULT_HISTORY_SIZE = 500;

    /**
     * Default maximum number of history entries to keep in the history file.
     * This value is used when the {@link LineReader#HISTORY_FILE_SIZE} variable is not set.
     */
    public static final int DEFAULT_HISTORY_FILE_SIZE = 10000;

    private final List<History.Entry> items = new ArrayList<>();

    private int index = 0;

    private LineReader reader;

    private Map<String, CustomHistory.HistoryFileData> historyFiles = new HashMap<>();

    public CustomHistory() {}

    public CustomHistory(LineReader reader) {
        attach(reader);
    }

    protected static class Entry implements History.Entry {
        private final int index;
        private final Instant time;
        private final String line;

        public Entry(int index, Instant time, String line) {
            this.index = index;
            this.time = time;
            this.line = line;
        }

        public Entry(int index, String line) {
            this.index = index;
            this.time = Instant.now();
            this.line = line;
        }

        @Override
        public int index() {
            return index;
        }

        @Override
        public Instant time() {
            return time;
        }

        @Override
        public String line() {
            return line;
        }


        @Override
        public String toString() {
            return String.format("%d: %s", index, line);
        }
    }

    protected CustomHistory.Entry createEntry(int index, Instant time, String line) {
        return new CustomHistory.Entry(index, time, line);
    }

    private static class HistoryFileData {
        private int lastLoaded = 0;
        private int entriesInFile = 0;

        public HistoryFileData() {}

        public HistoryFileData(int lastLoaded, int entriesInFile) {
            this.lastLoaded = lastLoaded;
            this.entriesInFile = entriesInFile;
        }

        public int getLastLoaded() {
            return lastLoaded;
        }

        public void setLastLoaded(int lastLoaded) {
            this.lastLoaded = lastLoaded;
        }

        public void decLastLoaded() {
            lastLoaded = lastLoaded - 1;
            if (lastLoaded < 0) {
                lastLoaded = 0;
            }
        }

        public int getEntriesInFile() {
            return entriesInFile;
        }

        public void setEntriesInFile(int entriesInFile) {
            this.entriesInFile = entriesInFile;
        }

        public void incEntriesInFile(int amount) {
            entriesInFile = entriesInFile + amount;
        }
    }


    // --- Basic operations required by JLine ---

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Returns the current index in the history.
     *
     * @return the current index
     */
    @Override
    public int index() {
        return index;
    }

    /**
     * Returns the index of the first element in the history.
     *
     * @return the index of the first history item
     */
    @Override
    public int first() {
        return 0;
    }

    /**
     * Returns the index of the last element in the history.
     *
     * @return the index of the last history item
     */
    @Override
    public int last() {
        return items.size() - 1;
    }

    @Override
    public String get(int index) {
        return items.get(index).line();
    }

    @Override
    public void add(Instant time, String line) {
        Objects.requireNonNull(time);
        Objects.requireNonNull(line);

        if (getBoolean(reader, LineReader.DISABLE_HISTORY, false)) {
            return;
        }
        if (isSet(reader, LineReader.Option.HISTORY_IGNORE_SPACE) && line.startsWith(" ")) {
            return;
        }
        if (isSet(reader, LineReader.Option.HISTORY_REDUCE_BLANKS)) {
            line = line.trim();
        }
        if (isSet(reader, LineReader.Option.HISTORY_IGNORE_DUPS)) {
            if (!items.isEmpty() && line.equals(items.get(items.size() - 1).line())) {
                return;
            }
        }
        if (matchPatterns(getString(reader, HISTORY_IGNORE, ""), line)) {
            return;
        }
        internalAdd(time, line);
        if (isSet(reader, LineReader.Option.HISTORY_INCREMENTAL)) {
            try {
                save();
            } catch (IOException e) {
                Log.warn("Failed to save history", e);
            }
        }
    }

    @Override
    public void add(String line) {
        int newIndex = items.size();
        items.add(new Entry(newIndex, line));
        index = newIndex;
    }

    /**
     * Returns a list iterator over the history entries starting at the specified index.
     *
     * @param index the index to start iterating from
     * @return a list iterator over the history entries
     */
    @Override
    public ListIterator<History.Entry> iterator(int index) {
        return items.listIterator(index);
    }

    @Override
    public ListIterator<History.Entry> iterator() {
        return iterator(first());
    }

    /**
     * Return the content of the current buffer.
     *
     * @return the content of the current buffer
     */
    @Override
    public String current() {
        if (index >= size()) {
            return "";
        }
        return items.get(index).line();
    }

    /**
     * Move the pointer to the previous element in the buffer.
     *
     * @return true if we successfully went to the previous element
     */
    @Override
    public boolean previous() {
        if (index <= 0) {
            return false;
        }
        index--;
        return true;
    }

    /**
     * Move the pointer to the next element in the buffer.
     *
     * @return true if we successfully went to the next element
     */
    @Override
    public boolean next() {
        if (index >= size()) {
            return false;
        }
        index++;
        return true;
    }

    /**
     * Moves the history index to the first entry.
     *
     * @return Return false if there are no iterator in the history or if the
     * history is already at the beginning.
     */
    @Override
    public boolean moveToFirst() {
        if (size() > 0 && index != 0) {
            index = 0;
            return true;
        }
        return false;
    }

    /**
     * This moves the history to the last entry. This entry is one position
     * before the moveToEnd() position.
     *
     * @return Returns false if there were no history iterator or the history
     * index was already at the last entry.
     */
    @Override
    public boolean moveToLast() {
        int lastEntry = size() - 1;
        if (lastEntry >= 0 && lastEntry != index) {
            index = size() - 1;
            return true;
        }

        return false;
    }

    /**
     * Move to the specified index in the history
     *
     * @param index The index to move to.
     * @return Returns true if the index was moved.
     */
    @Override
    public boolean moveTo(int index) {
        if (index >= 0 && index < size()) {
            this.index = index;
            return true;
        }
        return false;
    }

    /**
     * Move to the end of the history buffer. This will be a blank entry, after
     * all of the other iterator.
     */
    @Override
    public void moveToEnd() {
        index = size();
    }

    /**
     * Reset index after remove
     */
    @Override
    public void resetIndex() {
        index = Math.min(index, items.size());
    }


    // --- OPTIONAL: Support for persist / load ---

    public void load(File file) throws IOException {
        if (file == null || !file.exists()) return;
        read(file.toPath(), false);
    }

    public void save(File file) throws IOException {
        if (file == null) return;
        Path path = file.toPath();
        internalWrite(path, getLastLoaded(path));
    }

    // Unused JLine features (we can ignore or provide empty implementations)

    @Override public void attach(org.jline.reader.LineReader reader) {
        if (this.reader != reader) {
            this.reader = reader;
            try {
                load();
            } catch (IllegalArgumentException | IOException e) {
                Log.warn("Failed to load history", e);
            }
        }
    }

    /**
     * Load history.
     *
     * @throws IOException if a problem occurs
     */
    @Override
    public void load() throws IOException {
        Path path = getPath();
        if (path != null) {
            try {
                if (Files.exists(path)) {
                    Log.trace("Loading history from: ", path);
                    internalClear();
                    boolean hasErrors = false;

                    try (BufferedReader reader = Files.newBufferedReader(path)) {
                        List<String> lines = reader.lines().collect(java.util.stream.Collectors.toList());
                        for (String line : lines) {
                            try {
                                addHistoryLine(path, line);
                            } catch (IllegalArgumentException e) {
                                Log.debug("Skipping invalid history line: " + line, e);
                                hasErrors = true;
                            }
                        }
                    }

                    // 重置 index
                    index = size();
                    setHistoryFileData(path, new CustomHistory.HistoryFileData(items.size(), items.size()));
                    maybeResize();

                    // If we encountered errors, rewrite the history file with valid entries
                    if (hasErrors) {
                        Log.info("History file contained errors, rewriting with valid entries");
                        write(path, false);
                    }
                }
            } catch (IOException e) {
                Log.debug("Failed to load history; clearing", e);
                internalClear();
                throw e;
            }
        }
    }

    /**
     * Save history.
     *
     * @throws IOException if a problem occurs
     */
    @Override
    public void save() throws IOException {
        internalWrite(getPath(), getLastLoaded(getPath()));
    }

    /**
     * Write history to the file. If incremental only the events that are new since the last incremental operation to
     * the file are added.
     *
     * @param file        History file
     * @param incremental If true incremental write operation is performed.
     * @throws IOException if a problem occurs
     */
    @Override
    public void write(Path file, boolean incremental) throws IOException {
        Path path = file != null ? file : getPath();
        if (path != null && Files.exists(path)) {
            Files.deleteIfExists(path);
        }
        internalWrite(path, incremental ? getLastLoaded(path) : 0);
    }

    /**
     * Append history to the file. If incremental only the events that are new since the last incremental operation to
     * the file are added.
     *
     * @param file        History file
     * @param incremental If true incremental append operation is performed.
     * @throws IOException if a problem occurs
     */
    @Override
    public void append(Path file, boolean incremental) throws IOException {
        internalWrite(file != null ? file : getPath(), incremental ? getLastLoaded(file) : 0);
    }

    /**
     * Read history from the file. If checkDuplicates is <code>true</code> only the events that
     * are not contained within the internal list are added.
     *
     * @param file            History file
     * @param checkDuplicates If <code>true</code>, duplicate history entries will be discarded
     * @throws IOException if a problem occurs
     */
    @Override
    public void read(Path file, boolean checkDuplicates) throws IOException {
        Path path = file != null ? file : getPath();
        if (path != null) {
            try {
                if (Files.exists(path)) {
                    Log.trace("Reading history from: ", path);
                    boolean hasErrors = false;

                    try (BufferedReader reader = Files.newBufferedReader(path)) {
                        List<String> lines = reader.lines().collect(java.util.stream.Collectors.toList());
                        for (String line : lines) {
                            try {
                                addHistoryLine(path, line, checkDuplicates);
                            } catch (IllegalArgumentException e) {
                                Log.debug("Skipping invalid history line: " + line, e);
                                hasErrors = true;
                            }
                        }
                    }

                    setHistoryFileData(path, new CustomHistory.HistoryFileData(items.size(), items.size()));
                    maybeResize();

                    // If we encountered errors, rewrite the history file with valid entries
                    if (hasErrors) {
                        Log.info("History file contained errors, rewriting with valid entries");
                        write(path, false);
                    }
                }
            } catch (IOException e) {
                Log.debug("Failed to read history; clearing", e);
                internalClear();
                throw e;
            }
        }
    }

    @Override
    public void purge() throws IOException {
        internalClear();
        Path path = getPath();
        if (path != null) {
            Log.trace("Purging history from: ", path);
            Files.deleteIfExists(path);
        }
    }

    private Path getPath() {
        // 优先从环境变量获取信息
        String historyFile = System.getenv("HISTFILE");
        Path path = historyFile != null && !historyFile.isEmpty() ? Paths.get(historyFile) : null;
        Object obj = path != null ? path : reader != null ? reader.getVariables().get(LineReader.HISTORY_FILE) : null;
        if (obj instanceof Path) {
            return (Path) obj;
        } else if (obj instanceof File) {
            return ((File) obj).toPath();
        } else if (obj != null) {
            return Paths.get(obj.toString());
        } else {
            return null;
        }
    }

    private void internalClear() {
        index = 0;
        historyFiles = new HashMap<>();
        items.clear();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\n':
                    sb.append('\\');
                    sb.append('n');
                    break;
                case '\r':
                    sb.append('\\');
                    sb.append('r');
                    break;
                case '\\':
                    sb.append('\\');
                    sb.append('\\');
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }
        return sb.toString();
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\') {
                ch = s.charAt(++i);
                if (ch == 'n') {
                    sb.append('\n');
                } else if (ch == 'r') {
                    sb.append('\r');
                } else {
                    sb.append(ch);
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    protected boolean matchPatterns(String patterns, String line) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < patterns.length(); i++) {
            char ch = patterns.charAt(i);
            if (ch == '\\') {
                ch = patterns.charAt(++i);
                sb.append(ch);
            } else if (ch == ':') {
                sb.append('|');
            } else if (ch == '*') {
                sb.append('.').append('*');
            } else {
                sb.append(ch);
            }
        }
        return line.matches(sb.toString());
    }

    protected void internalAdd(Instant time, String line) {
        internalAdd(time, line, false);
    }

    protected void internalAdd(Instant time, String line, boolean checkDuplicates) {
        int newIndex = items.size();
        History.Entry entry = new Entry(newIndex , time, line);
        if (checkDuplicates) {
            for (History.Entry e : items) {
                if (e.line().trim().equals(line.trim())) {
                    return;
                }
            }
        }
        items.add(entry);
        index = newIndex;
        maybeResize();
    }

    private void maybeResize() {
        while (size() > getInt(reader, LineReader.HISTORY_SIZE, DEFAULT_HISTORY_SIZE)) {
            items.remove(0);
            for (CustomHistory.HistoryFileData hfd : historyFiles.values()) {
                hfd.decLastLoaded();
            }
        }
        index = size();
    }

    private void internalWrite(Path path, int from) throws IOException {
        if (path != null) {
            Log.trace("Saving history to: ", path);
            Path parent = path.toAbsolutePath().getParent();
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            // Append new items to the history file
            try (BufferedWriter writer = Files.newBufferedWriter(
                    path.toAbsolutePath(),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.CREATE)) {
                for (History.Entry entry : items.subList(from, items.size())) {
                    if (isPersistable(entry)) {
                        writer.append(format(entry));
                    }
                }
            }
            incEntriesInFile(path, items.size() - from);
            int max = getInt(reader, LineReader.HISTORY_FILE_SIZE, DEFAULT_HISTORY_FILE_SIZE);
            if (getEntriesInFile(path) > max + max / 4) {
                trimHistory(path, max);
            }
        }
        setLastLoaded(path, items.size());
    }

    private String doHistoryFileDataKey(Path path) {
        return path != null ? path.toAbsolutePath().toString() : null;
    }

    private CustomHistory.HistoryFileData getHistoryFileData(Path path) {
        String key = doHistoryFileDataKey(path);
        if (!historyFiles.containsKey(key)) {
            historyFiles.put(key, new CustomHistory.HistoryFileData());
        }
        return historyFiles.get(key);
    }

    private void setHistoryFileData(Path path, CustomHistory.HistoryFileData historyFileData) {
        historyFiles.put(doHistoryFileDataKey(path), historyFileData);
    }

    private boolean isLineReaderHistory(Path path) throws IOException {
        Path lrp = getPath();
        if (lrp == null) {
            return path == null;
        }
        return Files.isSameFile(lrp, path);
    }

    private void setLastLoaded(Path path, int lastloaded) {
        getHistoryFileData(path).setLastLoaded(lastloaded);
    }

    private void setEntriesInFile(Path path, int entriesInFile) {
        getHistoryFileData(path).setEntriesInFile(entriesInFile);
    }

    private void incEntriesInFile(Path path, int amount) {
        getHistoryFileData(path).incEntriesInFile(amount);
    }

    private int getLastLoaded(Path path) {
        return getHistoryFileData(path).getLastLoaded();
    }

    private int getEntriesInFile(Path path) {
        return getHistoryFileData(path).getEntriesInFile();
    }

    protected void addHistoryLine(Path path, String line) {
        addHistoryLine(path, line, false);
    }

    /**
     * Adds a history line to the specified history file with an option to check for duplicates.
     *
     * @param path the path to the history file
     * @param line the line to add
     * @param checkDuplicates whether to check for duplicate entries
     */
    protected void addHistoryLine(Path path, String line, boolean checkDuplicates) {
        if (reader.isSet(LineReader.Option.HISTORY_TIMESTAMPED)) {
//            int idx = line.indexOf(':');
//            final String badHistoryFileSyntax = "Bad history file syntax! " + "The history file `" + path
//                    + "` may be an older history: " + "please remove it or use a different history file.";
//            if (idx < 0) {
//                throw new IllegalArgumentException(badHistoryFileSyntax);
//            }
//            Instant time;
//            try {
//                time = Instant.ofEpochMilli(Long.parseLong(line.substring(0, idx)));
//            } catch (DateTimeException | NumberFormatException e) {
//                throw new IllegalArgumentException(badHistoryFileSyntax);
//            }
//
//            String unescaped = unescape(line.substring(idx + 1));
            internalAdd(Instant.now(), unescape(line), checkDuplicates);
        } else {
            internalAdd(Instant.now(), unescape(line), checkDuplicates);
        }
    }

    protected void trimHistory(Path path, int max) throws IOException {
        Log.trace("Trimming history path: ", path);
        // Load all history entries
        LinkedList<History.Entry> allItems = new LinkedList<>();
        try (BufferedReader historyFileReader = Files.newBufferedReader(path)) {
            List<String> lines = historyFileReader.lines().collect(java.util.stream.Collectors.toList());
            for (String l : lines) {
                try {
//                    if (reader.isSet(LineReader.Option.HISTORY_TIMESTAMPED)) {
//                        int idx = l.indexOf(':');
//                        if (idx < 0) {
//                            Log.debug("Skipping invalid history line: " + l);
//                            continue;
//                        }
//                        try {
//                            Instant time = Instant.ofEpochMilli(Long.parseLong(l.substring(0, idx)));
//                            String line = unescape(l.substring(idx + 1));
//                            allItems.add(createEntry(allItems.size(), time, line));
//                        } catch (DateTimeException | NumberFormatException e) {
//                            Log.debug("Skipping invalid history timestamp: " + l);
//                        }
//                    } else {
//                        allItems.add(createEntry(allItems.size(), Instant.now(), unescape(l)));
//                    }
                    allItems.add(createEntry(allItems.size(), Instant.now(), unescape(l)));
                } catch (Exception e) {
                    Log.debug("Skipping invalid history line: " + l, e);
                }
            }
        }
        // Remove duplicates
        List<History.Entry> trimmedItems = doTrimHistory(allItems, max);
        // Write history
        Path temp = Files.createTempFile(
                path.toAbsolutePath().getParent(), path.getFileName().toString(), ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardOpenOption.WRITE)) {
            for (History.Entry entry : trimmedItems) {
                writer.append(format(entry));
            }
        }
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        // Keep items in memory
        if (isLineReaderHistory(path)) {
            internalClear();
//            offset = trimmedItems.get(0).index();
            items.addAll(trimmedItems);
            setHistoryFileData(path, new CustomHistory.HistoryFileData(items.size(), items.size()));
        } else {
            setEntriesInFile(path, allItems.size());
        }
        maybeResize();
    }

    static List<History.Entry> doTrimHistory(List<History.Entry> allItems, int max) {
        int idx = 0;
        while (idx < allItems.size()) {
            int ridx = allItems.size() - idx - 1;
            String line = allItems.get(ridx).line().trim();
            ListIterator<History.Entry> iterator = allItems.listIterator(ridx);
            while (iterator.hasPrevious()) {
                String l = iterator.previous().line();
                if (line.equals(l.trim())) {
                    iterator.remove();
                }
            }
            idx++;
        }
        while (allItems.size() > max) {
            allItems.remove(0);
        }
        int  index = allItems.get(allItems.size() - 1).index() - allItems.size() + 1;
        List<History.Entry> out   = new ArrayList<>();
        for (History.Entry e : allItems) {
            out.add(new CustomHistory.Entry(index++, e.time(), e.line()));
        }
        return out;
    }

    private String format(History.Entry entry) {
//        if (reader.isSet(LineReader.Option.HISTORY_TIMESTAMPED)) {
//            return entry.time().toEpochMilli() + ":" + escape(entry.line()) + "\n";
//        }
        return escape(entry.line()) + "\n";
    }
}
