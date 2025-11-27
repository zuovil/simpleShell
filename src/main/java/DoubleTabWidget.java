import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.reader.Widget;
import org.jline.terminal.Terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DoubleTabWidget implements Widget {

    private final LineReader reader;

    private final List<String> commands;

    private Boolean showMatchesOnNextTab = false;

    public DoubleTabWidget(LineReader reader, List<String> commands) {
        this.reader = reader;
        this.commands = commands;
    }

    @Override
    public boolean apply() {
        Terminal terminal = reader.getTerminal();;
        String line= reader.getBuffer().toString();
        int pos = reader.getBuffer().cursor();

        String prefix = line.substring(0, pos); // 光标前的内容
        List<String> matches = commands.stream().filter(cmd -> cmd.startsWith(prefix)).collect(Collectors.toList());

        try {
            if (matches.isEmpty()) {
                terminal.writer().print("\007"); // 响铃
                terminal.flush();
            } else if (matches.size() == 1) {
                // 唯一匹配 → 补全余下字符
                String match = matches.get(0);
                match = match + " ";
                reader.getBuffer().clear();
                reader.getBuffer().write(match);
            } else {
                // 多个匹配
                if (showMatchesOnNextTab) {
                    // 第二次 Tab → 打印所有候选
                    terminal.writer().println();
                    terminal.writer().println(String.join("  ", matches));
                    terminal.flush();
                    showMatchesOnNextTab = false;
                    // 重绘当前行
                    reader.callWidget(LineReader.REDRAW_LINE);
                } else {
                    // 第一次 Tab → 响铃，标记下一次 Tab
                    terminal.writer().print("\007");
                    terminal.flush();
                    showMatchesOnNextTab = true;
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

        return true;
    }
}
