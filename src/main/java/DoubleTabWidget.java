import org.jline.reader.LineReader;
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
        Terminal terminal = reader.getTerminal();
        String line= reader.getBuffer().toString();
        int pos = reader.getBuffer().cursor();

        String prefix = line.substring(0, pos); // 光标前的内容
        List<String> matches = commands.stream().filter(cmd -> cmd.startsWith(prefix)).sorted().collect(Collectors.toList());

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
                String commonPrefix = getShortestPrefix(prefix, matches); // 获取最短公共前缀

                if (!commonPrefix.equals(prefix)) {
                    // 如果有更新的公共前缀，补全前缀
                    reader.getBuffer().clear();
                    reader.getBuffer().write(commonPrefix); // 将公共前缀写入命令行
                } else if (showMatchesOnNextTab) {
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

    // 从候选列表获取最短前缀
    private String getShortestPrefix(String prefix, List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return "";
        }
        String commonPrefix = commands.get(0);

        // 遍历其它命令并逐步缩短公共前缀
        for (int i = 1; i < commands.size(); i++) {
            String command = commands.get(i);

            // 比较公共前缀与每个命令的前缀
            int j = 0;
            while (j < commonPrefix.length() && j < command.length() && commonPrefix.charAt(j) == command.charAt(j)) {
                j++;
            }

            // 更新公共前缀
            commonPrefix = commonPrefix.substring(0, j);

            // 如果没有共同前缀，返回空字符串
            if (commonPrefix.isEmpty()) {
                return "";
            }
        }

        return commonPrefix; // 返回最终的公共前缀
    }
}
