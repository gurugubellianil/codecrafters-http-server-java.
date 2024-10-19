import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
  public static void main(String[] args) {
    final int port = 4221;
    final int maxThreads = 10;
    ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);

    try (ServerSocket serverSocket = new ServerSocket(port)) {
      serverSocket.setReuseAddress(true);
      System.out.println("Server is running on port " + port);

      while (true) {
        // Accept incoming client connections
        Socket clientSocket = serverSocket.accept();
        System.out.println("Accepted new connection");

        // Submit a new task to the thread pool to handle each client connection
        threadPool.submit(() -> handleRequest(clientSocket));
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }

  private static void handleRequest(Socket clientSocket) {
    try (InputStream input = clientSocket.getInputStream();
         BufferedReader reader = new BufferedReader(new InputStreamReader(input));
         OutputStream output = clientSocket.getOutputStream()) {

      // Read the first line of the request
      String line = reader.readLine();
      System.out.println("Request: " + line);

      // Handle the request and respond
      String[] HttpRequest = line.split(" ");
      if (HttpRequest[1].equals("/")) {
        output.write("HTTP/1.1 200 OK\r\n\r\n".getBytes());
      } else {
        output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
      }
      output.flush();

    } catch (IOException e) {
      System.out.println("IOException while handling request: " + e.getMessage());
    } finally {
      try {
        clientSocket.close();
      } catch (IOException e) {
        System.out.println("IOException on socket close: " + e.getMessage());
      }
    }
  }
}
