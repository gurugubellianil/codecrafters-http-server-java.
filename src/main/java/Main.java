import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
  public static void main(String[] args) {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.out.println("Logs from your program will appear here!");
    
//     Uncomment this block to pass the first stage
    ServerSocket serverSocket = null;
    Socket clientSocket = null;
     try {
       serverSocket = new ServerSocket(4221);
    
       // Since the tester restarts your program quite often, setting SO_REUSEADDR
       // ensures that we don't run into 'Address already in use' errors
       serverSocket.setReuseAddress(true);
    
       clientSocket = serverSocket.accept(); // Wait for connection from client.
       System.out.println("accepted new connection");
       InputStream input = clientSocket.getInputStream();
       BufferedReader reader = new BufferedReader(new InputStreamReader(input));
       String line = reader.readLine();
       System.out.println(line);
       String[] HttpRequest = line.split(" ");
       OutputStream output = clientSocket.getOutputStream();
       if(HttpRequest[1].equals("/")) {
    	   output.write("HTTP/1.1 200 OK\r\n\r\n".getBytes());
       }
       else if (HttpRequest[1].startsWith("/echo/")) {
           String queryParam = HttpRequest[1].split("/")[2];
           output.write(
               ("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " +
                queryParam.length() + "\r\n\r\n" + queryParam)
                   .getBytes());
       } 
       else {
    	   output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
       }
     } catch (IOException e) {
       System.out.println("IOException: " + e.getMessage());
     }
     finally {
         // Close the sockets in a safe way
         try {
             if (clientSocket != null) {
                 clientSocket.close();
             }
             if (serverSocket != null) {
                 serverSocket.close();
             }
         } catch (IOException e) {
             System.out.println("IOException on close: " + e.getMessage());
         }
     }
  }
}
