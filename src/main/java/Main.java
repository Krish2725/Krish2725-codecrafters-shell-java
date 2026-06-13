import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        int k =100;
        while(k==100){
        Scanner in= new Scanner(System.in);
        System.out.print("$ ");
        String command =in.nextLine();
        System.out.print(command+ ": command not found");
        System.out.println();
        }
    }
}
