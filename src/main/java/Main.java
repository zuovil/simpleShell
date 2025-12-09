import org.jline.reader.*;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        Map<String, String> pathMap = getEnv();
        Path historyFilePath = getHistoryPath();
        try (Terminal terminal = TerminalBuilder.builder().system(true).provider("jni").build()) {
            List<String> commands  = new ArrayList<>(pathMap.keySet());
            List<String> builtinCommands = new ArrayList<>(Arrays.asList("echo", "cat","type", "exit"));
            commands.addAll(builtinCommands);
            commands = commands.stream().distinct().sorted().collect(Collectors.toList());
            Completer completer = new StringsCompleter(commands);
            // 使用终端
            LineReader lineReader = LineReaderBuilder.builder()
                                                     .terminal(terminal)
                                                     .completer(completer) // 自动补全
                                                     .history(new CustomHistory())
                                                     .option(LineReader.Option.HISTORY_IGNORE_DUPS, false) // 允许重复记录
                                                     .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true) // 禁用自动转义
                                                     .option(LineReader.Option.AUTO_MENU, false)
//                                                     .variable(LineReader.HISTORY_FILE,
//                                                               java.nio.file.Paths.get("history.txt")) // 持久化文件

                                                     .build();
            DoubleTabWidget widget = new DoubleTabWidget(lineReader, commands);
            lineReader.getWidgets().put("double-tab", widget);
            // 绑定 Tab 键到自定义 widget
            lineReader.getKeyMaps().get(LineReader.MAIN).bind(new Reference("double-tab"), "\t");
//            int lastAppendIndex = 0;
//            // 预载入index
//            int preloadIndex = 0;

            // 预载入历史记录
//            if(historyFilePath != null) {
//                History history = lineReader.getHistory();
//                List<String> historyList = Files.readAllLines(historyFilePath);
//                for(String historyCommand : historyList) {
//                    history.add(historyCommand);
//                }
//                preloadIndex = history.last();
//            }

            while (true) {
                String input = lineReader.readLine("$ ");

                if (input.equals("exit 0") | input.equals("exit")) {
//                    if(historyFilePath != null) {
//                        History history = lineReader.getHistory();
//                        // 坑： History的迭代器存在重写 public int index() {return offset + index;} 因此遍历得到的History
//                        // .Entry并不是按顺序的得出的结果，多次运行结果每一次都可能不一样，因此需要使用List来重置其迭代器
//                        List<History.Entry> list = new ArrayList<>();
//                        for (History.Entry e : history) list.add(e);
//                        list = list.stream().sorted(Comparator.comparing(History.Entry::index)).collect(Collectors.toList());
//                        if (Files.notExists(historyFilePath)) {
//                            Files.createFile(historyFilePath);
//                        }
//                        List<String> appendHistory = new ArrayList<>();
//                        if(preloadIndex != 0) {
//                            for(History.Entry entry : list) {
//                                if(entry.index() > preloadIndex) {
//                                    appendHistory.add(entry.line());
//                                }
//                            }
//                        } else {
//                            for (History.Entry e : list) appendHistory.add(e.line());
//                        }
//                        try(BufferedWriter br = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(historyFilePath, StandardOpenOption.APPEND)))){
//                            for(String line : appendHistory) {
//                                br.append(line);
//                                br.newLine();
//                            }
//                            br.flush();
//                        }
//                    }
                    break;
                }
                Command command = Command.fromInput(input);
                if (command == null) {
                    continue;
                }
                String commandName = command.getCommandName();
                List<String> params = command.getArgs();
                if ("echo".equals(commandName)) {
                    // 检测重定向
                    if (params.contains(">") || params.contains("1>") || params.contains("2>")) {
                        redirectOutput(params, commandName);
                        continue;
                    }
                    if (params.contains(">>") || params.contains("1>>") || params.contains("2>>")) {
                        redirectAppendOutput(params, commandName);
                        continue;
                    }
                    if (params.contains("|")) {
                        params.removeIf(" "::equals);
                        pipelineOutput(params, commandName);
                        continue;
                    }
                    for (String param : params) {
                        System.out.print(param);
                    }
                    System.out.println();
                    continue;
                }

                if ("cat".equals(commandName)) {
                    // 移除空格
                    params.removeIf(" "::equals);
                    // 检测重定向
                    if (params.contains(">") || params.contains("1>") || params.contains("2>")) {
                        redirectOutput(params, commandName);
                        continue;
                    }
                    if (params.contains(">>") || params.contains("1>>") || params.contains("2>>")) {
                        redirectAppendOutput(params, commandName);
                        continue;
                    }
                    if (params.contains("|")) {
                        pipelineOutput(params, commandName);
                        continue;
                    }
                    params.add(0, "cat");
                    Process process = new ProcessBuilder(params).start();
                    //新启两个线程
                    DealProcessStream out = new DealProcessStream(process.getInputStream());
                    DealProcessStream err = new DealProcessStream(process.getErrorStream());
                    out.start();
                    err.start();
                    out.join();
                    err.join();
                    process.waitFor();
                    // destroy() 只在需要强制杀进程时使用。否则可能正在读，IO就关闭了，然后报错Stream closed
//                process.destroy();
                    continue;

                }
                if ("history".equals(commandName)) {
                    History history = lineReader.getHistory();
                    // History的index存在重写，return offset + index，得到的不是真实值，因此需要使用默认迭代器的index
//                    List<History.Entry> list = new ArrayList<>();
//                    for (History.Entry e : history) list.add(e);
//                    list = list.stream().sorted(Comparator.comparing(History.Entry::index)).collect(Collectors.toList());
                    if(params.isEmpty()) {
                        for (History.Entry entry : history) {
                            System.out.println("    " + (entry.index() + 1) + "  " + entry.line());
                        }
                    } else if (params.size() > 2) {
                            if("-r".equals(params.get(0))) {
                                String historyFilePathStr = params.get(2);
//                                List<String> historyList = new ArrayList<>();
//                                try(BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(historyFilePathStr))))){
//                                    String line;
//                                    while ((line = br.readLine()) != null) {
//                                        historyList.add(line);
//                                    }
//                                }
//                                for(String historyCommand : historyList) {
//                                    history.add(historyCommand);
//                                }
                                history.read(Paths.get(historyFilePathStr), false);

                            } else if("-w".equals(params.get(0))) {
                                String historyFilePathStr = params.get(2);
//                                Path path = Paths.get(historyFilePathStr);
//                                if (Files.notExists(path)) {
//                                    Files.createFile(path);
//                                }
//                                try(BufferedWriter br = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path)))){
//                                    for(History.Entry entry : history) {
//                                        br.write(entry.line());
//                                        br.newLine();
//                                    }
//                                    br.flush();
//                                }
                                history.write(Paths.get(historyFilePathStr), false);
                            } else if("-a".equals(params.get(0))) {
                                String historyFilePathStr = params.get(2);
//                                Path path = Paths.get(historyFilePathStr);
//                                List<String> appendHistory = new ArrayList<>();
//                                if(lastAppendIndex != 0) {
//                                    for(History.Entry entry : list) {
//                                        if(entry.index() > lastAppendIndex) {
//                                            appendHistory.add(entry.line());
//                                        }
//                                    }
//                                } else {
//                                    for (History.Entry e : list) appendHistory.add(e.line());
//                                }
//
//                                try(BufferedWriter br =
//                                            new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path, StandardOpenOption.APPEND)))){
//                                    for(String line : appendHistory) {
//                                        br.append(line);
//                                        br.newLine();
//                                    }
//                                    br.flush();
//                                }
//                                lastAppendIndex = history.last();
                                history.append(Paths.get(historyFilePathStr), true);
                            }

                    } else {
                    int count = Integer.parseInt(params.get(0));
                    int size = history.size();
                    // 只打印最后 count 条
                    for (int i = size - count; i < size; i++) {
                        String line = history.get(i);
                        System.out.println("    " + (i + 1) + "  " + line);
                    }
                }
                    continue;

                }
                if ("type".equals(commandName)) {
                    Set<String> builtin = new HashSet<>(Arrays.asList("type", "echo", "exit","history"));
                    String      arg     = String.join(" ", params);
                    if (builtin.contains(arg)) {
                        System.out.println(arg + " is a shell builtin");
                        continue;
                    }

                    if (pathMap.containsKey(arg)) {
                        System.out.println(arg + " is " + pathMap.get(arg));
                        continue;
                    }

                    System.out.println(arg + ": not found");
                    continue;
                }

                if (pathMap.containsKey(commandName)) {
                    // 移除空格
                    params.removeIf(" "::equals);
                    // 检测重定向
                    if (params.contains(">") || params.contains("1>") || params.contains("2>")) {
                        redirectOutput(params, commandName);
                        continue;
                    }
                    if (params.contains(">>") || params.contains("1>>") || params.contains("2>>")) {
                        redirectAppendOutput(params, commandName);
                        continue;
                    }
                    if (params.contains("|")) {
                        pipelineOutput(params, commandName);
                        continue;
                    }
                    params.add(0, commandName);
                    Process process = Runtime.getRuntime().exec(params.toArray(new String[0]));
                    // 得到process的输出的方式是getInputStream，这是因为我们要从Java 程序的角度来看，外部程序的输出对于Java来说就是输入，反之亦然。
                    // 外部程序在执行结束后需自动关闭，否则不管是字符流还是字节流均由于既读不到数据，又读不到流结束符，从而出现阻塞Java进程运行的情况
                    // 如果exec启动的Process没有正确处理（stdout/stderr 有一个未读，进程未 waitFor），导致资源没关闭、管道没释放，于是 JVM
                    // 内部执行挂起（或资源耗尽），从而将会影响到下一次exec
                    // Java只有一套 System.in/out/err（线程共享JVM的所有资源），线程可以自己创建别的流如FileOutputStream，这些都是线程自己持有的对象，不是“线程独立 IO”
                    // 遇到process流阻塞通常有两个方法解决，一个是并发处理两个流信息，开启两个线程分别处理输出流与错误流（仅在同一个线程处理两个流依旧会发生阻塞，因为尽管看上去同步但仍有先后顺序，所以必须用线程并发）
                    // 2.将两个流合并为一个流，使用ProcessBuilder，将其redirectErrorStream(true)；将输出流与错误流合并
                    DealProcessStream out = new DealProcessStream(process.getInputStream());
                    DealProcessStream err = new DealProcessStream(process.getErrorStream());
                    out.start();
                    err.start();
                    out.join();
                    err.join();
                    process.waitFor();
                    continue;
                }
                System.out.println(input + ": command not found");
            }
        }
    }

    // 检测到重定向标识符进行输出重定向。方法抽象
    public static Map<String, String> getEnv() {
        Map<String, String> env = new HashMap<>();
        String osName = System.getProperty("os.name").toLowerCase();
        String PATH = System.getenv("PATH");
        String[] paths;
        if(osName.contains("win")){
            paths = PATH.split(";");
        } else {
            paths = PATH.split(":");
        }

        for(String path : paths){
            File parent = new File(path);
            if(parent.exists()) {
                if(parent.isDirectory()) {
                    Map<String, String> fileNameMap = Arrays.stream(Objects.requireNonNull(parent.listFiles((dir, name) -> new File(dir, name).isFile())))
                                                        .filter(File::canExecute)
                                                        .collect(Collectors.toMap(file -> file.getName().lastIndexOf(".") != -1 ?
                                                                                          file.getName().substring(0, file.getName().lastIndexOf(".")) : file.getName(),
                                                                                  File::getAbsolutePath,
                                                                                  (v1, v2) -> v2));
                    if(!fileNameMap.isEmpty()){
                        env.putAll(fileNameMap);
                    }
                } else {
                    if(parent.canExecute()) {
                        String fileName = parent.getName().lastIndexOf(".") != -1 ?
                                parent.getName().substring(0, parent.getName().lastIndexOf(".")) : parent.getName();
                        env.put(fileName, parent.getAbsolutePath());
                    }
                }
            }
        }
        return env;
    }

    // 获取历史文件路径
    private static Path getHistoryPath() {
        String historyFile = System.getenv("HISTFILE");
        if(historyFile == null) {
            return null;
        }
        return Paths.get(historyFile);
    }

    private static void redirectOutput(List<String> params, String commandName) throws Exception {
        // 检测重定向
        if(params.contains(">") || params.contains("1>") || params.contains("2>")) {
            // 移除空格
            params.removeIf(" "::equals);
            boolean isRedirect = false;
            boolean stderr = false;
            String redirectFileName = params.get(params.size() - 1);
            List<String> beforeRedirect = new ArrayList<>();
            for(String param : params) {
                if(param.equals(">") || param.equals("1>") || param.equals("2>")) {
                    isRedirect = true;
                    if(param.equals("2>")) {
                        stderr = true;
                    }
                }
                if(!isRedirect) {
                    beforeRedirect.add(param);
                }
            }
            beforeRedirect.add(0, commandName);
            if(isRedirect) {
                ProcessBuilder processBuilder = new ProcessBuilder(beforeRedirect);

                if(!stderr) {
                    processBuilder.redirectOutput(new File(redirectFileName));
                    Process process = processBuilder.start();
                    DealProcessStream err = new DealProcessStream(process.getErrorStream());
                    err.start();
                    err.join();
                    process.waitFor();
                } else {
                    processBuilder.redirectError(new File(redirectFileName));
                    Process process = processBuilder.start();
                    DealProcessStream out = new DealProcessStream(process.getInputStream());
                    out.start();
                    out.join();
                    process.waitFor();
                }
            }
        }
    }

    // 检测>>，追加输出到文件内容末尾
    private static void redirectAppendOutput(List<String> params, String commandName) throws Exception {
        // 检测重定向
        if(params.contains(">>") || params.contains("1>>") || params.contains("2>>")) {
            // 移除空格
            params.removeIf(" "::equals);
            boolean isRedirect = false;
            boolean stderr = false;
            String redirectFileName = params.get(params.size() - 1);
            List<String> beforeRedirect = new ArrayList<>();
            for(String param : params) {
                if(param.equals(">>") || param.equals("1>>") || param.equals("2>>")) {
                    isRedirect = true;
                    if(param.equals("2>>")) {
                        stderr = true;
                    }
                }
                if(!isRedirect) {
                    beforeRedirect.add(param);
                }
            }
            beforeRedirect.add(0, commandName);
            if(isRedirect) {
                ProcessBuilder processBuilder = new ProcessBuilder(beforeRedirect);

                if(!stderr) {
                    processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(redirectFileName)));
                    Process process = processBuilder.start();
                    DealProcessStream err = new DealProcessStream(process.getErrorStream());
                    err.start();
                    err.join();
                    process.waitFor();
                } else {
                    processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(new File(redirectFileName)));
                    Process process = processBuilder.start();
                    DealProcessStream out = new DealProcessStream(process.getInputStream());
                    out.start();
                    out.join();
                    process.waitFor();
                }
            }
        }
    }

    private static void pipelineOutput(List<String> params, String commandName) throws Exception {
        // 检测管道输入
        if(params.contains("|")) {
            List<String> commands = new ArrayList<>();
            commands.add("sh");
            commands.add("-c");
            params.add(0, commandName);
            String input = String.join(" ", params);
            commands.add(input);
            // sh -c 允许用户在命令行中直接运行一段字符串形式的 Shell 脚本或命令
            Process process = new ProcessBuilder(commands).start();
            DealProcessStream out = new DealProcessStream(process.getInputStream());
            out.start();
            out.join();
            process.waitFor();
        }
    }

}
