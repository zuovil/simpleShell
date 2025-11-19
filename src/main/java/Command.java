import java.util.*;

public class Command {
    public static final Set<Character> quoteSymbols = new HashSet<>(Arrays.asList('\'', '"', '`'));
    private final String commandName;
    private final List<String> args;

    Command(String cmdName) {
        commandName = cmdName;
        args = new ArrayList<>();
    }

    public static Command fromInput(String input)
            throws IllegalArgumentException {
        if (input.isEmpty()) {
            return null;
        }

        Command cmd = null;
        StringBuilder sb = new StringBuilder();
        int i;
        // 解析命令
        for (i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == ' ') {
                if (cmd == null && !isEmpty(sb)) {
                    cmd = new Command(sb.toString());
                    sb.delete(0, sb.length());
                }
            } else if (cmd != null) {
                break;
            } else {
                sb.append(ch);
            }
        }

        //如果单命令没有参数写入命令并返回
        if (!isEmpty(sb)) {
            cmd = new Command(sb.toString());
            sb.delete(0, sb.length());
        } else if (cmd == null) {
            return null;
        }

        char quoteChar = '\0';
        boolean isSpace = false;
        // 解析参数
        for (; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == ' ') {
                if (quoteChar != '\0') {
                    sb.append(ch);
                } else if (!isSpace) {
                    isSpace = true;
                    if (!isEmpty(sb)) {
                        cmd.addArg(sb.toString());
                        sb.delete(0, sb.length());
                    }
                }
            } else {
                // 遇到非空字符关闭标记
                if (isSpace) {
                    isSpace = false;
                    // 特殊的遇到引号间隔之中连续空格的情况，此类情况标记为一个空格
                    cmd.addArg(" ");
                }

                sb.append(ch);
                if (quoteSymbols.contains(ch)) {
                    if (quoteChar == ch) {
                        quoteChar = '\0';
                        cmd.addArg(sb.toString());
                        sb.delete(0, sb.length());
                    } else if (quoteChar == '\0') {
                        quoteChar = ch;
                        if (sb.length() > 1) {
                            cmd.addArg(sb.substring(0, sb.length() - 1));
                            sb.delete(0, sb.length() - 1);
                        }
                    }
                }
            }
        }

        if (!isEmpty(sb)) {
            if (isSpace) {
                cmd.addArg(" ");
            }

            cmd.addArg(sb.toString());
        }

        return cmd;
    }

    public void addArg(String arg) { args.add(arg); }

    public String getCommandName() { return commandName.toLowerCase(); }

    public String[] getArgs() { return args.toArray(new String[0]); }

    // 再次转译参数（去除引号）
    public String[] getArgsSanitized() {
        String[] arguments = getArgs();
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i].isEmpty())
                continue;
            if (arguments[i].length() > 1) {
                char firstChar = arguments[i].charAt(0);
                char lastChar = arguments[i].charAt(arguments[i].length() - 1);
                if (firstChar == lastChar && quoteSymbols.contains(firstChar)) {
                    arguments[i] = arguments[i].substring(1, arguments[i].length() - 1);
                }
            }
        }

        return arguments;
    }

    private static boolean isEmpty(StringBuilder sb) {
        return sb == null || sb.length() == 0;
    }
}