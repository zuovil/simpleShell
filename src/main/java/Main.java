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
            System.out.println(input + ": command not found");
        }
    }
}
