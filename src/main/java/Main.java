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
            String command = input.split(" ")[0];
            if("echo".equals(command)) {
                String newStr= input.substring(input.indexOf(input.split(" ")[1]));
                long count = newStr.chars().filter(c -> c == '\'').count();
                if(count == 0 || count == 1) {
                    System.out.println(newStr.replaceAll("\\s+", " "));
                    continue;
                }
                if(count % 2 == 0) {
                    if(newStr.endsWith("'")) {
                        System.out.println(String.join("", newStr.split("'")));
                    } else {
                        String after = newStr.substring(newStr.lastIndexOf("'") + 1).replaceAll("\\s+", " ");
                        String before = newStr.substring(0, newStr.lastIndexOf("'"));
                        String newStr1 = before + after;
                        System.out.println(String.join("", newStr1.split("'")));
                    }
                    continue;
                } else {
                    String after = newStr.substring(newStr.lastIndexOf("'")).replaceAll("\\s+", " ");
                    String before = newStr.substring(0, newStr.lastIndexOf("'"));
                    System.out.println(String.join("", before.split("'")) + after);
                    continue;
                }
            }

            if("cat".equals(command)) {
                String paramString = input.substring(input.indexOf(input.split(" ")[1]));
                char[] chars = paramString.toCharArray();
                List<Integer> singleQuoteList = new ArrayList<>();
                List<String> params = new ArrayList<>();
                for(int i = 0; i < chars.length; i++) {
                    if(chars[i] == '\'') {
                        singleQuoteList.add(i);
                    }
                }
                int count = singleQuoteList.size();
                if(!paramString.startsWith("'") || count < 2) {
                    System.out.println();
                    continue;
                }

                if(count % 2 != 0) {
                    singleQuoteList.remove(singleQuoteList.size() - 1);
                }

                for (int i = 0; i < count - 1; i++) {
                    String param = paramString.substring(singleQuoteList.get(i) + 1, singleQuoteList.get(i + 1));
                    if(i % 2 == 0) {
                        params.add(param);
                    }
                }
                params.add(0, "cat");
                Process process = new ProcessBuilder(params).start();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    while (br.ready()) {
                        System.out.println(br.readLine());
                    }
                }
                process.destroy();
                continue;

            }
            if("type".equals(command)) {
                String newStr= input.substring(input.indexOf(input.split(" ")[1]));
                // 特殊处理
                if(newStr.equals("type type")){
                    System.out.println("type is a shell builtin");
                    continue;
                }
                if("echo".equals(newStr) || "exit".equals(newStr)) {
                    System.out.println(newStr + " is a shell builtin");
                    continue;
                }

                if(pathMap.containsKey(newStr)){
                    System.out.println(newStr + " is " + pathMap.get(newStr));
                    continue;
                }

                System.out.println(newStr + ": not found");
                continue;
            }
            if(pathMap.containsKey(command)){
                Process process = Runtime.getRuntime().exec(input.split(" "));
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    while (br.ready()) {
                        System.out.println(br.readLine());
                    }
                }
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
