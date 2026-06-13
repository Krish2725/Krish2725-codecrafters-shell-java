import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        int k =100;
        Scanner in= new Scanner(System.in);
        while(k==100){
        
        System.out.print("$ ");
        String command =in.nextLine();
        if(command.contains("type exit")){
            // System.exit(0);
            System.out.println(command.substring(5)+" is a shell builtin");
        }
        else if(command.contains("type echo")){
        // System.out.print(command.substring(5));
        // System.out.println();
        System.out.println(command.substring(5)+" is a shell builtin");
        }
        else if(command.contains("type type")){
            System.out.println(command.substring(5)+" is a shell builtin");
        }
        else if(command.contains("exit")){
            System.exit(0);
            
        }
        else if(command.contains("echo")){
        System.out.print(command.substring(5));
        System.out.println();
        }
        
        else if(command.startsWith("type")){
        System.out.print(command.substring(5)+ ": not found");
        System.out.println();
        }
        else{
            System.out.print(command+ ": command not found");
        System.out.println();
        }
        }
    }
}
