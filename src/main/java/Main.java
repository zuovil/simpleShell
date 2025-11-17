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
                System.out.println(newStr);
                continue;
            }
            if("type".equals(command)) {
                String newStr= input.substring(input.indexOf(input.split(" ")[1]));
                // 特殊处理
                if(newStr.equals("type type")){
                    System.out.println("type is a shell builtin");
                    continue;
                }
                if("echo".equals(newStr) || "exit".equals(newStr)){
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
                continue;
            }
            System.out.println(input + ": command not found");
        }
    }

    public static Map<String, String> getEnv() {
        Map<String, String> env = new HashMap<>();
        String osName = System.getProperty("os.name").toLowerCase();
        String PATH = System.getenv("PATH");
        String[] paths = PATH.split(";");
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
