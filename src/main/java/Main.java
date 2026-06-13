import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        int k =100;
        Scanner in= new Scanner(System.in);
        while(k==100){
        
        System.out.print("$ ");
        String command =in.nextLine();
        if(command.equals("exit")){
            System.exit(0);
        }
        else if(command.startsWith("echo")){
        System.out.print(command.substring(5));
        System.out.println();
        }
        else{
        System.out.print(command+ ": command not found");
        System.out.println();
        }
        }
    }
}
