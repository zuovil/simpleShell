import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        while(true){
            System.out.print("$ ");
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine();
            if(input.equals("exit 0")){
                break;
            }
            if((input.split(" ")[0]).equals("echo")){
                String newStr= input.substring(input.indexOf(input.split(" ")[1]));
                System.out.println(newStr);
                continue;
            }
            if((input.split(" ")[0]).equals("type")){
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
                System.out.println(newStr + ": not found");
                continue;
            }
            System.out.println(input + ": command not found");
        }
    }
}
