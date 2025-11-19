import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        while(true) {
            Map<String, String> pathMap = getEnv();
            System.out.print("$ ");
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine();
            if(input.equals("exit 0")){
                break;
            }
            Command command = Command.fromInput(input);
            if(command == null){
                continue;
            }
            String commandName = command.getCommandName();
            List<String> params = new ArrayList<>(Arrays.asList(command.getArgsSanitized()));;
            if("echo".equals(commandName)) {
                for (String param : params) {
                    System.out.print(param);
                }
                System.out.println();
                continue;
            }

            if("cat".equals(commandName)) {
                params.add(0, "cat");
                Process process = new ProcessBuilder(params).start();
                //新启两个线程
                new DealProcessStream(process.getInputStream()).start();
                new DealProcessStream(process.getErrorStream()).start();
                process.waitFor();
                process.destroy();
                continue;

            }
            if("type".equals(commandName)) {
                Set<String> builtin = new HashSet<>(Arrays.asList("type", "echo", "exit"));
                String arg = String.join(" ", params);
                if(builtin.contains(arg)){
                    System.out.println(arg + " is a shell builtin");
                    continue;
                }

                if(pathMap.containsKey(arg)){
                    System.out.println(arg + " is " + pathMap.get(arg));
                    continue;
                }

                System.out.println(arg + ": not found");
                continue;
            }

            if(pathMap.containsKey(commandName)){
                // 移除空格
                params.removeIf(" "::equals);
                params.add(0, commandName);
                Process process = Runtime.getRuntime().exec(params.toArray(new String[0]));
                // 得到process的输出的方式是getInputStream，这是因为我们要从Java 程序的角度来看，外部程序的输出对于Java来说就是输入，反之亦然。
                // 外部程序在执行结束后需自动关闭，否则不管是字符流还是字节流均由于既读不到数据，又读不到流结束符，从而出现阻塞Java进程运行的情况
                // 如果exec启动的Process没有正确处理（stdout/stderr 有一个未读，进程未 waitFor），导致资源没关闭、管道没释放，于是 JVM
                // 内部执行挂起（或资源耗尽），从而将会影响到下一次exec
                // Java只有一套 System.in/out/err（线程共享JVM的所有资源），线程可以自己创建别的流如FileOutputStream，这些都是线程自己持有的对象，不是“线程独立 IO”
                // 遇到process流阻塞通常有两个方法解决，一个是并发处理两个流信息，开启两个线程分别处理输出流与错误流（仅在同一个线程处理两个流依旧会发生阻塞，因为尽管看上去同步但仍有先后顺序，所以必须用线程并发）
                // 2.将两个流合并为一个流，使用ProcessBuilder，将其redirectErrorStream(true)；将输出流与错误流合并
                new DealProcessStream(process.getInputStream()).start();
                new DealProcessStream(process.getErrorStream()).start();
                process.waitFor();
                process.destroy();
                continue;
            }
            System.out.println(input + ": command not found");
        }
    }

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
                        String fileName = parent.getName().lastIndexOf(".") != -1 ? parent.getName().substring(0, parent.getName().lastIndexOf(".")) : parent.getName();
                        env.put(fileName, parent.getAbsolutePath());
                    }
                }
            }
        }
        return env;
    }
}
